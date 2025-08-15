INSERT INTO sc_signer (id, timestamp_created, timestamp_last_updated, external_signer_id, user_id, csr, certificate, timestamp_certificate_expiration, status) VALUES
    (1, '2020-01-01 00:00:00', '2020-01-01 00:00:00', '1', 1, '1', '1', '2020-01-01 00:00:00', 'CREATED');

INSERT INTO sc_document_content (id, content) VALUES
    (1, '1'),
    (2, '2'),
    (3, '3'),
    (4, '4');

INSERT INTO sc_document (id, document_content_id, document_id, document_name, external_id, file_name, file_size, hash, signature, signer_id, status, timestamp_created) VALUES
    (1, 1, '1', 'test', '1', 'test.pdf', 100, '1', '1', 1, 'WAITING', now() - interval '2' day),
    (2, 2, '2', 'test', '2', 'test.pdf', 100, '1', '1', 1, 'REJECTED', now() - interval '1' hour),
    (3, 3, '3', 'test', '3', 'test.pdf', 100, '1', '1', 1, 'SIGNED', now() - interval '2' day),
<file moved to src/test/resources/com/wultra/signercloud/server/document/DocumentServiceTest.sql>
