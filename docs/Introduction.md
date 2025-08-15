# Introduction

Wultra CloudSigner is a powerful and secure mobile solution that enables users to sign PDF documents using their mobile phones. The signing process leverages strong customer authentication (SCA) provided by PowerAuth to ensure high security and user identity verification.

## Key Features

- **Seamless Mobile Signing**: Users can digitally sign PDF documents from their mobile devices, ensuring a smooth and efficient experience.
- **Strong Customer Authentication (SCA)**: Signing operations are protected by PowerAuth, ensuring only authenticated users can perform digital signatures.
- **Certification Authority Integration**: The generated certificate is signed by a Certification Authority (CA) component to guarantee its validity and trustworthiness.
- **Flexible Signing Options - External and Cloud**: The solution can either provide signatures using the private key stored on a mobile device as part of PowerAuth SDK or provide whole PKI infrastructure on the backend and use the PowerAuth only as a loosely coupled SCA provider.

**End-to-End Security**: The process—from user authentication to signature generation—is designed with strong security and integrity in mind. The solution is on the Wultra roadmap to **quantum-resistant authentication**.

## Standards

CloudSigner supports PDF Advanced Electronic Signatures (PAdES) defined by ETSI EN 319 142 standard with level **PAdES-B-B** (Basic Signature).

PAdES is [recognized by eIDAS](https://ec.europa.eu/digital-building-blocks/sites/display/DIGITAL/Standards+and+specifications#Standardsandspecifications-PAdES(PDFAdvancedElectronicSignature)BaselineProfile) as [Advanced Electronic Signature](https://ec.europa.eu/digital-building-blocks/sites/display/DIGITAL/What+is+eSignature#WhatiseSignature-AdvancedElectronicSignatures(AdES)) (AdES).

CloudSigner supports **PDF 2.0** documents defined by ISO 32000-2 standard.