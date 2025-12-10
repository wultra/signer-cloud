-- Insert into sc_signer
INSERT INTO "sc_signer"
("id", "timestamp_created", "timestamp_last_updated", "external_signer_id",
 "user_id", "csr", "certificate", "timestamp_certificate_expiration", "status")
VALUES
    (1, TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2020-01-01 00:00:00', '1', 1,
     UTL_RAW.CAST_TO_RAW('1'), UTL_RAW.CAST_TO_RAW('1'), TIMESTAMP '2020-01-01 00:00:00', 'ACTIVE');

-- Insert into sc_document_content
INSERT ALL
    INTO "sc_document_content" ("id", "content") VALUES (1, UTL_RAW.CAST_TO_RAW('1'))
    INTO "sc_document_content" ("id", "content") VALUES (2, UTL_RAW.CAST_TO_RAW('2'))
    INTO "sc_document_content" ("id", "content") VALUES (3, UTL_RAW.CAST_TO_RAW('3'))
    INTO "sc_document_content" ("id", "content") VALUES (4, UTL_RAW.CAST_TO_RAW('4'))
SELECT * FROM dual;

-- Insert into sc_document
INSERT ALL
    INTO "sc_document" ("id", "document_content_id", "document_id", "document_name",
                        "external_id", "file_name", "file_size", "hash", "signature",
                        "signer_id", "status", "timestamp_created")
    VALUES (1, 1, '1', 'test', '1', 'test.pdf', 100, UTL_RAW.CAST_TO_RAW('1'),
            UTL_RAW.CAST_TO_RAW('1'), 1, 'WAITING', SYSTIMESTAMP - INTERVAL '2' DAY)
    INTO "sc_document" ("id", "document_content_id", "document_id", "document_name",
                        "external_id", "file_name", "file_size", "hash", "signature",
                        "signer_id", "status", "timestamp_created")
    VALUES (2, 2, '2', 'test', '2', 'test.pdf', 100, UTL_RAW.CAST_TO_RAW('1'),
            UTL_RAW.CAST_TO_RAW('1'), 1, 'REJECTED', SYSTIMESTAMP - INTERVAL '1' HOUR)
    INTO "sc_document" ("id", "document_content_id", "document_id", "document_name",
                        "external_id", "file_name", "file_size", "hash", "signature",
                        "signer_id", "status", "timestamp_created")
    VALUES (3, 3, '3', 'test', '3', 'test.pdf', 100, UTL_RAW.CAST_TO_RAW('1'),
            UTL_RAW.CAST_TO_RAW('1'), 1, 'SIGNED', SYSTIMESTAMP - INTERVAL '2' DAY)
    INTO "sc_document" ("id", "document_content_id", "document_id", "document_name",
                        "external_id", "file_name", "file_size", "hash", "signature",
                        "signer_id", "status", "timestamp_created")
    VALUES (4, 4, '4', 'test', '4', 'test.pdf', 100, UTL_RAW.CAST_TO_RAW('1'),
            UTL_RAW.CAST_TO_RAW('1'), 1, 'WAITING', SYSTIMESTAMP - INTERVAL '1' HOUR)
SELECT * FROM dual;
