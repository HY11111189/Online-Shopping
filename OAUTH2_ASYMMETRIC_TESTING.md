# OAuth2 Asymmetric JWT Testing Guide

## Overview
Your application now uses **OAuth 2.0 with Asymmetric JWT (RSA)** authentication instead of symmetric JWT. 

### Key Differences:
- **Before**: Tokens signed and verified with a shared secret (symmetric)
- **After**: Tokens signed with RSA private key, verified with RSA public key (asymmetric)
- **Security**: More secure - public key can be shared, private key stays on server
- **Standard**: Follows OAuth 2.0 and OpenID Connect standards

## 1. Register a New User
