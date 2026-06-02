package it.domheadroom.mc_ticket.service;

import it.domheadroom.mc_ticket.dto.LoginRequest;
import it.domheadroom.mc_ticket.dto.LoginResponse;
import it.domheadroom.mc_ticket.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public ResponseEntity<?> login(LoginRequest request) {
        var userOpt = userRepository.findByEmail(request.getEmail());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Credenziali non valide"));
        }

        var user = userOpt.get();

        if (!user.getIsActive()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Utente disabilitato"));
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Credenziali non valide"));
        }

        String token = jwtService.generateToken(user.getEmail(), user.getRole());
        return ResponseEntity.ok(new LoginResponse(token, user.getEmail(), user.getRole(), user.getFullName()));
    }
}
