package com.dms.provenance;

import com.dms.allocation.Allocation;
import com.dms.user.User;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What was true about one dissertation when the certificate was issued.
 *
 * <p>The digest covers the immutable facts. Verification recomputes it from live
 * data and compares, so an edit made after issue shows up as a mismatch. The
 * frozen payload is kept alongside so the verify page can show what was claimed
 * even once the register has moved on.
 */
@Entity
@Table(name = "certificates")
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    /** The public handle, printed on the document and embedded in the QR. */
    @Column(nullable = false, unique = true, length = 24)
    String code;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocation_id", nullable = false, unique = true)
    Allocation allocation;

    @Column(nullable = false, length = 64)
    String digest;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    Map<String, String> payload = new LinkedHashMap<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_by")
    User issuedBy;

    @Column(name = "issued_at", nullable = false)
    Instant issuedAt = Instant.now();

    @Column(name = "revoked_at")
    Instant revokedAt;

    public boolean isRevoked() {
        return revokedAt != null;
    }
}
