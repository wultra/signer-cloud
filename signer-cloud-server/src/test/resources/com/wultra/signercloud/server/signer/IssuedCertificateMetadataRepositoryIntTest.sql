-- ===========================================
-- Insert into sc_signer
-- ===========================================
INSERT ALL
    INTO "sc_signer"
        ("id", "timestamp_created", "external_signer_id", "user_id",
         "csr", "timestamp_certificate_expiration", "status")
    VALUES (
        1,
        SYSTIMESTAMP,
        '756419e1-1d85-4172-815d-d8653ecd3a89',
        'test-user',
        UTL_RAW.CAST_TO_RAW('MIHxMIGYAgEAMDYxETAPBgNVBAMMCEpvaG4gRG9lMRQwEgYDVQQKDAtFeGFtcGxlQ29ycDELMAkGA1UEBhMCVVMwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAT4i0arrfMJ+3mkipWWQRY33l1uoLWUttTzTEselqaNxk+GNLnQy9GW7KBaB9RZ4LhreWEJMDfjO1prlCFFxxgmoAAwCgYIKoZIzj0EAwIDSAAwRQIhAOTV4jyWM0hIg3iRT8Xh//JGmEjFgN+wVJiYRI2Zl5nzAiAeoKKXtYzzU5VxqrqkbylVSPdSzgsetPvt/arRNQhNfw=='),
        SYSTIMESTAMP + INTERVAL '1' YEAR,
        'ACTIVE'
    )
    INTO "sc_signer"
        ("id", "timestamp_created", "external_signer_id", "user_id",
         "csr", "timestamp_certificate_expiration", "status")
    VALUES (
        2,
        SYSTIMESTAMP,
        'd20b836d-21f3-42d2-81c4-b1492cbf4379',
        'test-user',
        UTL_RAW.CAST_TO_RAW('MIIB+DCCAX6gAwIBAgIUKC4hJLtXk82IALkmzb6s/HcrsgMwCgYIKoZIzj0EAwMwFDESMBAGA1UEAwwJSXNzdWluZ0NBMB4XDTI1MDkxMjA5MTE0NFoXDTI3MDgxMTA5MTQ0NlowNjERMA8GA1UEAwwISm9obiBEb2UxFDASBgNVBAoMC0V4YW1wbGVDb3JwMQswCQYDVQQGEwJVUzBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABOvUMi73HbZtISS3WUk/iF/oCDEfPZPK6IBNoFbX2G4oxEHVdArN0N39koovt8Zo2ZkJQQzaSa4Ii/hbt5aetkmjgYswgYgwDAYDVR0TAQH/BAIwADAfBgNVHSMEGDAWgBSdHZNQyT/Ly6g/w8deRDDhKZqpDjAoBgNVHSUEITAfBggrBgEFBQcDAgYIKwYBBQUHAwQGCSqGSIb3LwEBBTAdBgNVHQ4EFgQU2PPiHgo5PGWHUhQNiylNjvsHIOIwDgYDVR0PAQH/BAQDAgXgMAoGCCqGSM49BAMDA2gAMGUCMQCLiToIz6/shohas6/jdgE9HRnwcwrtsXy/Yws+Zbe+a0IssHhekxBRo3YPIlqEMmMCMFqzaXux6TrJtP/eb2AYxcvT8kh7Xp9UybiqHcNyFb086rZGNQJGCXjbfbCHVzWj4A=='),
        SYSTIMESTAMP + INTERVAL '1' YEAR,
        'ACTIVE'
    )
SELECT * FROM dual;


-- ===========================================
-- Insert into sc_issued_certificate_metadata
-- ===========================================
INSERT ALL
    INTO "sc_issued_certificate_metadata"
        ("id", "signer_id", "timestamp_created", "serial_number",
         "issuer_dn", "timestamp_certificate_expiration", "status")
    VALUES (
        1, 1, SYSTIMESTAMP,
        '464473996258166929563006265940531964676283587162',
        'CN=IssuingCA',
        SYSTIMESTAMP - INTERVAL '1' YEAR,
        'ISSUED'
    )
    INTO "sc_issued_certificate_metadata"
        ("id", "signer_id", "timestamp_created", "serial_number",
         "issuer_dn", "timestamp_certificate_expiration", "status")
    VALUES (
        2, 1, SYSTIMESTAMP,
        '150115369802686049496198416221037898187985335988',
        'CN=IssuingCA',
        SYSTIMESTAMP + INTERVAL '1' YEAR,
        'REVOKED'
    )
    INTO "sc_issued_certificate_metadata"
        ("id", "signer_id", "timestamp_created", "serial_number",
         "issuer_dn", "timestamp_certificate_expiration", "status")
    VALUES (
        3, 2, SYSTIMESTAMP,
        '376883810783522850827079784290574723800061976659',
        'CN=IssuingCA',
        SYSTIMESTAMP + INTERVAL '1' YEAR,
        'ISSUED'
    )
    INTO "sc_issued_certificate_metadata"
        ("id", "signer_id", "timestamp_created", "serial_number",
         "issuer_dn", "timestamp_certificate_expiration", "status")
    VALUES (
        4, 1, SYSTIMESTAMP,
        '188194009031705707572915518141583833328109468304',
        'CN=IssuingCA',
        SYSTIMESTAMP + INTERVAL '1' YEAR,
        'ISSUED'
    )
SELECT * FROM dual;
