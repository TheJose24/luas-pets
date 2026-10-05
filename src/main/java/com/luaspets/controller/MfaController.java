package com.luaspets.controller;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import com.luaspets.exception.BusinessException;
import com.luaspets.model.Rol;
import com.luaspets.security.CustomUserDetails;
import com.luaspets.security.MfaSession;
import com.luaspets.security.RoleBasedAuthenticationSuccessHandler;
import com.luaspets.security.TotpSecretCipher;
import com.luaspets.security.TotpService;
import com.luaspets.service.MfaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/2fa")
public class MfaController {
    private static final Duration SETUP_LIFETIME = Duration.ofMinutes(10);
    private static final int QR_SIZE = 280;
    private final MfaService mfa;
    private final TotpService totp;
    private final TotpSecretCipher cipher;
    private final Clock clock;
    public MfaController(MfaService mfa, TotpService totp, TotpSecretCipher cipher, Clock clock) {
        this.mfa = mfa;
        this.totp = totp;
        this.cipher = cipher;
        this.clock = clock;
    }
    @ModelAttribute
    public void noCache(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Referrer-Policy", "no-referrer");
    }
    @GetMapping
    public String verifyPage() {
        return "2fa/verificar";
    }
    @PostMapping
    public String verify(@AuthenticationPrincipal CustomUserDetails principal, @RequestParam String codigo,
        @RequestParam(defaultValue = "false") boolean recuperacion, HttpServletRequest request, Model model) {
        var session = request.getSession();
        if (session.getAttribute(MfaSession.STATE) != MfaSession.State.PENDING)
            return "redirect:/login";
        if (!MfaSession.attempt(session))
            return terminate(request);
        try {
            if (mfa.verify(
                    principal.getUsuario().getId(), principal.getSecurityVersion(), codigo, recuperacion)) {
                request.changeSessionId();
                session.setAttribute(MfaSession.STATE, MfaSession.State.COMPLETE);
                MfaSession.resetAttempts(session);
                return "redirect:"
                    + RoleBasedAuthenticationSuccessHandler.dashboard(principal.getUsuario().getRol());
            }
        } catch (BusinessException | IllegalStateException e) { /* generic error only */
        }
        model.addAttribute("error", "El código ingresado no es válido.");
        return "2fa/verificar";
    }
    @GetMapping("/configurar")
    public String setup(
        @AuthenticationPrincipal CustomUserDetails principal, HttpServletRequest request, Model model) {
        if (principal.getUsuario().isTwoFactorEnabled())
            return "redirect:/perfil";
        var session = request.getSession();
        if (!validSetup(session)) {
            if (principal.getUsuario().getRol() != Rol.ADMIN || !passwordRecent(session))
                return "2fa/iniciar";
            provision(session);
        }
        return showSetup(principal, session, model);
    }
    @PostMapping("/configurar")
    public String start(@AuthenticationPrincipal CustomUserDetails principal,
        @RequestParam String passwordActual, HttpServletRequest request, Model model) {
        var session = request.getSession();
        if (principal.getUsuario().isTwoFactorEnabled())
            return "redirect:/perfil";
        if (!MfaSession.attempt(session))
            return terminate(request);
        if (!mfa.passwordValid(principal.getUsuario().getId(), passwordActual)) {
            model.addAttribute("error", "No se pudo verificar la solicitud.");
            return "2fa/iniciar";
        }
        session.setAttribute(MfaSession.PASSWORD_CONFIRMED, clock.instant());
        provision(session);
        return showSetup(principal, session, model);
    }
    @PostMapping("/activar")
    public String activate(@AuthenticationPrincipal CustomUserDetails principal, @RequestParam String codigo,
        HttpServletRequest request, Model model) {
        var session = request.getSession();
        if (!MfaSession.attempt(session))
            return terminate(request);
        if (!validSetup(session)) {
            MfaSession.clearSetup(session);
            return "redirect:/2fa/configurar";
        }
        try {
            var codes = mfa.enable(principal.getUsuario().getId(), principal.getSecurityVersion(),
                (String) session.getAttribute(MfaSession.PROVISIONAL), codigo);
            MfaSession.clearSetup(session);
            model.addAttribute("codigos", codes);
            return "2fa/recuperacion";
        } catch (BusinessException | IllegalStateException e) {
            model.addAttribute("error", "El código ingresado no es válido.");
            return showSetup(principal, session, model);
        }
    }
    @PostMapping("/desactivar")
    public String disable(@AuthenticationPrincipal CustomUserDetails principal,
        @RequestParam String passwordActual, @RequestParam String codigo, HttpServletRequest request,
        RedirectAttributes flash) {
        if (!MfaSession.attempt(request.getSession()))
            return terminate(request);
        try {
            mfa.disable(
                principal.getUsuario().getId(), principal.getSecurityVersion(), passwordActual, codigo);
            return terminate(request);
        } catch (BusinessException | IllegalStateException e) {
            flash.addFlashAttribute("errorMfa", "No se pudo verificar la solicitud.");
            return "redirect:/perfil";
        }
    }
    @PostMapping("/recuperacion")
    public String regenerate(@AuthenticationPrincipal CustomUserDetails principal,
        @RequestParam String passwordActual, @RequestParam String codigo, HttpServletRequest request,
        Model model, RedirectAttributes flash) {
        if (!MfaSession.attempt(request.getSession()))
            return terminate(request);
        try {
            model.addAttribute("codigos",
                mfa.regenerate(
                    principal.getUsuario().getId(), principal.getSecurityVersion(), passwordActual, codigo));
            return "2fa/recuperacion";
        } catch (BusinessException | IllegalStateException e) {
            flash.addFlashAttribute("errorMfa", "No se pudo verificar la solicitud.");
            return "redirect:/perfil";
        }
    }
    private void provision(HttpSession session) {
        session.setAttribute(MfaSession.PROVISIONAL, cipher.encrypt(totp.generateSecret()));
        session.setAttribute(MfaSession.EXPIRES,
            ((Instant) session.getAttribute(MfaSession.PASSWORD_CONFIRMED)).plus(SETUP_LIFETIME));
    }
    private boolean passwordRecent(HttpSession session) {
        return session.getAttribute(MfaSession.PASSWORD_CONFIRMED) instanceof Instant confirmed
            && !confirmed.isAfter(clock.instant())
            && clock.instant().isBefore(confirmed.plus(SETUP_LIFETIME));
    }

    private boolean validSetup(HttpSession session) {
        return passwordRecent(session) && session.getAttribute(MfaSession.PROVISIONAL) instanceof String
            && session.getAttribute(MfaSession.EXPIRES) instanceof Instant expires
            && clock.instant().isBefore(expires);
    }
    private String showSetup(CustomUserDetails principal, HttpSession session, Model model) {
        String secret = cipher.decrypt((String) session.getAttribute(MfaSession.PROVISIONAL));
        model.addAttribute("secreto", secret);
        model.addAttribute("qr", qr(totp.uri(secret, principal.getUsername())));
        return "2fa/configurar";
    }
    private String qr(String uri) {
        try {
            var matrix = new QRCodeWriter().encode(uri, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
            var image = new BufferedImage(QR_SIZE, QR_SIZE, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < QR_SIZE; x++)
                for (int y = 0; y < QR_SIZE; y++)
                    image.setRGB(x, y, matrix.get(x, y) ? 0 : 0xffffff);
            var bytes = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", bytes);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el QR.");
        }
    }
    private String terminate(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null)
            request.getSession(false).invalidate();
        return "redirect:/login?reauth";
    }
}
