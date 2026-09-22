package com.expensetracker.expensetracker.security.passkey;

import com.expensetracker.expensetracker.model.Passkey;
import com.expensetracker.expensetracker.repository.PasskeyRepository;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.exception.Base64UrlException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** How the passkey library looks up stored passkeys. Usernames here are email addresses. */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PasskeyCredentialRepository implements CredentialRepository {

    private final PasskeyRepository passkeyRepository;

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        return passkeyRepository.findByUserEmail(username).stream()
                .map(passkey -> PublicKeyCredentialDescriptor.builder().id(bytes(passkey.getCredentialId())).build())
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return passkeyRepository.findByUserEmail(username).stream().findFirst().map(passkey -> bytes(passkey.getUserHandle()));
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        return passkeyRepository.findByUserHandle(userHandle.getBase64Url()).stream().findFirst()
                .map(passkey -> passkey.getUser().getEmail());
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        return passkeyRepository.findByCredentialId(credentialId.getBase64Url()).stream()
                .filter(passkey -> passkey.getUserHandle().equals(userHandle.getBase64Url()))
                .findFirst()
                .map(PasskeyCredentialRepository::toRegistered);
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        return passkeyRepository.findByCredentialId(credentialId.getBase64Url()).stream()
                .map(PasskeyCredentialRepository::toRegistered)
                .collect(Collectors.toSet());
    }

    private static RegisteredCredential toRegistered(Passkey passkey) {
        return RegisteredCredential.builder()
                .credentialId(bytes(passkey.getCredentialId()))
                .userHandle(bytes(passkey.getUserHandle()))
                .publicKeyCose(bytes(passkey.getPublicKeyCose()))
                .signatureCount(passkey.getSignatureCount())
                .build();
    }

    static ByteArray bytes(String base64Url) {
        try {
            return ByteArray.fromBase64Url(base64Url);
        } catch (Base64UrlException ex) {
            throw new IllegalStateException("Stored passkey data is corrupt", ex);
        }
    }
}
