package com.ticketya.auth.service;

import com.ticketya.auth.dto.AuthResponse;
import com.ticketya.auth.dto.LoginRequest;
import com.ticketya.auth.dto.RegisterRequest;
import com.ticketya.auth.model.Usuario;
import com.ticketya.auth.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El email ya está registrado");
        }
        Usuario usuario = new Usuario(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.rol()
        );
        usuarioRepository.save(usuario);
        String token = jwtService.generarToken(usuario.getId(), usuario.getRol().name());
        return new AuthResponse(token, usuario.getId(), usuario.getRol().name());
    }

    public AuthResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas"));

        if (!passwordEncoder.matches(request.password(), usuario.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        }

        String token = jwtService.generarToken(usuario.getId(), usuario.getRol().name());
        return new AuthResponse(token, usuario.getId(), usuario.getRol().name());
    }
}
