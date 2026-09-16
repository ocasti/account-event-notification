package co.cobre.notifications.infrastructure.security;

import co.cobre.notifications.domain.ClientId;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Resolves the client ID from a JWT token.
 */
@Component
public class ClientIdResolver {
    private final JwtProperties props;

    
    public ClientIdResolver(JwtProperties props) {
        this.props = props;
    }

    /**
     * Resolves the client ID from a JWT token.
     */
    public ClientId resolve(Jwt jwt) {
        String claim = jwt.getClaimAsString(props.clientClaim());
        if (claim == null) {
            throw new IllegalArgumentException("Missing required claim: " + props.clientClaim());
        }
        return new ClientId(claim);
    }
}
