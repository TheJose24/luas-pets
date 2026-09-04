package com.luaspets.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luaspets.exception.ResourceNotFoundException;
import com.luaspets.model.EstadoMascota;
import com.luaspets.model.Mascota;
import com.luaspets.model.Usuario;
import com.luaspets.repository.MascotaRepository;
import com.luaspets.repository.UsuarioRepository;

@Service
public class MascotaService {

    private final MascotaRepository mascotaRepository;
    private final UsuarioRepository usuarioRepository;

    public MascotaService(MascotaRepository mascotaRepository, UsuarioRepository usuarioRepository) {
        this.mascotaRepository = mascotaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public Mascota registrarMascota(Mascota mascota, Long clienteId) {
        Usuario cliente = usuarioRepository.findById(clienteId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con id: " + clienteId));
        mascota.setCliente(cliente);
        if (mascota.getEstado() == null) {
            mascota.setEstado(EstadoMascota.ACTIVO);
        }
        return mascotaRepository.save(mascota);
    }

    public List<Mascota> listarPorCliente(Long clienteId) {
        return mascotaRepository.findByClienteId(clienteId);
    }

    public Mascota buscarPorId(Long id) {
        return mascotaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mascota no encontrada con id: " + id));
    }

    public Mascota buscarPorIdConCliente(Long id) {
        return mascotaRepository.findWithClienteById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mascota no encontrada con id: " + id));
    }
}
