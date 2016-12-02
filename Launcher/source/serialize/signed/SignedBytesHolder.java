package launcher.serialize.signed;

import java.io.IOException;
import java.security.SignatureException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;

import launcher.LauncherAPI;
import launcher.helper.SecurityHelper;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launcher.serialize.stream.StreamObject;

public class SignedBytesHolder extends StreamObject {
    protected final byte[] bytes;
    private final byte[] sign;

    @LauncherAPI
    public SignedBytesHolder(HInput input, RSAPublicKey publicKey) throws IOException, SignatureException {
        this(input.readByteArray(0), input.readByteArray(-SecurityHelper.RSA_KEY_LENGTH), publicKey);
    }

    @LauncherAPI
    public SignedBytesHolder(byte[] bytes, byte[] sign, RSAPublicKey publicKey) throws SignatureException {
        SecurityHelper.verifySign(bytes, sign, publicKey);
        this.bytes = Arrays.copyOf(bytes, bytes.length);
        this.sign = Arrays.copyOf(sign, sign.length);
    }

    @LauncherAPI
    public SignedBytesHolder(byte[] bytes, RSAPrivateKey privateKey) {
        this.bytes = Arrays.copyOf(bytes, bytes.length);
        sign = SecurityHelper.sign(bytes, privateKey);
    }

    @Override
    public final void write(HOutput output) throws IOException {
        output.writeByteArray(bytes, 0);
        output.writeByteArray(sign, -SecurityHelper.RSA_KEY_LENGTH);
    }

    @LauncherAPI
    public final byte[] getBytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    @LauncherAPI
    public final byte[] getSign() {
        return Arrays.copyOf(sign, sign.length);
    }
}
