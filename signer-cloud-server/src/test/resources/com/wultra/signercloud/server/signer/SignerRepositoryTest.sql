INSERT ALL
    INTO "sc_signer"
        ("id", "timestamp_created", "external_signer_id", "user_id",
         "csr", "certificate", "timestamp_certificate_expiration", "status")
    VALUES (1,
            TIMESTAMP '2020-01-01 00:00:00',
            '3',
            1,
            UTL_RAW.CAST_TO_RAW('1'),
            UTL_RAW.CAST_TO_RAW('1'),
            TIMESTAMP '2099-01-01 00:00:00',
            'ACTIVE')

    INTO "sc_signer"
        ("id", "timestamp_created", "external_signer_id", "user_id",
         "csr", "certificate", "timestamp_certificate_expiration", "status")
    VALUES (2,
            TIMESTAMP '2020-01-01 00:00:00',
            '2',
            1,
            UTL_RAW.CAST_TO_RAW('1'),
            UTL_RAW.CAST_TO_RAW('1'),
            TIMESTAMP '2020-01-01 00:00:00',
            'BLOCKED')

    INTO "sc_signer"
        ("id", "timestamp_created", "external_signer_id", "user_id",
         "csr", "certificate", "timestamp_certificate_expiration", "status")
    VALUES (3,
            TIMESTAMP '2020-01-01 00:00:00',
            '1',
            1,
            UTL_RAW.CAST_TO_RAW('1'),
            UTL_RAW.CAST_TO_RAW('1'),
            TIMESTAMP '2020-01-01 00:00:00',
            'ACTIVE')

    INTO "sc_signer"
        ("id", "timestamp_created", "external_signer_id", "user_id",
         "csr", "certificate", "timestamp_certificate_expiration", "status")
    VALUES (4,
            TIMESTAMP '2020-01-01 00:00:00',
            '4',
            1,
            UTL_RAW.CAST_TO_RAW('1'),
            UTL_RAW.CAST_TO_RAW('1'),
            TIMESTAMP '2020-01-01 00:00:00',
            'REMOVED')

    INTO "sc_signer"
        ("id", "timestamp_created", "external_signer_id", "user_id",
         "csr", "certificate", "timestamp_certificate_expiration", "status")
    VALUES (5,
            TIMESTAMP '2020-01-01 00:00:00',
            '5',
            1,
            UTL_RAW.CAST_TO_RAW('1'),
            UTL_RAW.CAST_TO_RAW('1'),
            TIMESTAMP '2020-01-01 00:00:00',
            'ACTIVE')
SELECT * FROM dual;
