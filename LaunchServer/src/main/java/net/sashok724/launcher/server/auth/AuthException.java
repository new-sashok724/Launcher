package net.sashok724.launcher.server.auth;

import java.io.IOException;

import net.sashok724.launcher.client.LauncherAPI;

public final class AuthException extends IOException {
	private static final long serialVersionUID = -2586107832847245863L;

	@LauncherAPI
	public AuthException(String message) {
		super(message);
	}

	@Override
	public String toString() {
		return getMessage();
	}
}
