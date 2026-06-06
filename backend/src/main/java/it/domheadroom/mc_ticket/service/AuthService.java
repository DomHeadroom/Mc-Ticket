package it.domheadroom.mc_ticket.service;

import it.domheadroom.mc_ticket.dto.LoginRequest;
import it.domheadroom.mc_ticket.dto.LoginResponse;
import it.domheadroom.mc_ticket.exception.AuthException;
import it.domheadroom.mc_ticket.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

    /**
     * Autentica l'utente e restituisce i dati di sessione.
     *
     * @throws AuthException con status 401 se le credenziali sono errate o l'utente è disabilitato
     */
    public LoginResponse login(LoginRequest request) {
        var user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AuthException("Credenziali non valide", HttpStatus.UNAUTHORIZED));

        if (!user.getIsActive()) {
            throw new AuthException("Utente disabilitato", HttpStatus.UNAUTHORIZED);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AuthException("Credenziali non valide", HttpStatus.UNAUTHORIZED);
        }

        String token = jwtService.generateToken(user.getEmail(), user.getRole());
        return new LoginResponse(token, user.getEmail(), user.getRole(), user.getFullName());
    }
}
