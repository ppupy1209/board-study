package board.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "auth_member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long memberId;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 40)
    private String displayName;

    @Column(nullable = false)
    private Instant createdAt;

    public static AuthMember register(String email, String passwordHash, String displayName, Instant now) {
        AuthMember member = new AuthMember();
        member.email = email;
        member.passwordHash = passwordHash;
        member.displayName = displayName;
        member.createdAt = now;
        return member;
    }
}
