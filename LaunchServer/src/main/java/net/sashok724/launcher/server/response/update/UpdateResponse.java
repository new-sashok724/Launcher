package net.sashok724.launcher.server.response.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Deque;
import java.util.LinkedList;

import net.sashok724.launcher.client.hasher.HashedDir;
import net.sashok724.launcher.client.hasher.HashedEntry;
import net.sashok724.launcher.client.helper.IOHelper;
import net.sashok724.launcher.client.request.RequestException;
import net.sashok724.launcher.client.request.update.UpdateRequest;
import net.sashok724.launcher.client.serialize.HInput;
import net.sashok724.launcher.client.serialize.HOutput;
import net.sashok724.launcher.client.serialize.signed.SignedObjectHolder;
import net.sashok724.launcher.server.LaunchServer;
import net.sashok724.launcher.server.response.Response;

public final class UpdateResponse extends Response {
	public UpdateResponse(LaunchServer server, long id, HInput input, HOutput output) {
		super(server, id, input, output);
	}

	@Override
	public void reply() throws IOException {
		// Read update dir name
		String updateDirName = IOHelper.verifyFileName(input.readString(255));
		SignedObjectHolder<HashedDir> hdir = server.getUpdateDir(updateDirName);
		if (hdir == null) {
			throw new RequestException(String.format("Unknown update dir: %s", updateDirName));
		}
		writeNoError(output);

		// Write update hdir
		debug("Update dir: '%s'", updateDirName);
		hdir.write(output);
		output.flush();

		// Prepare variables for actions queue
		Path dir = LaunchServer.UPDATES_DIR.resolve(updateDirName);
		Deque<HashedDir> dirStack = new LinkedList<>();
		dirStack.add(hdir.object);

		// Perform update
		UpdateRequest.Action[] actionsSlice = new UpdateRequest.Action[UpdateRequest.MAX_QUEUE_SIZE];
		loop:
		while (true) {
			// Read actions slice
			int length = input.readLength(actionsSlice.length);
			for (int i = 0; i < length; i++) {
				actionsSlice[i] = new UpdateRequest.Action(input);
			}

			// Perform actions
			for (int i = 0; i < length; i++) {
				UpdateRequest.Action action = actionsSlice[i];
				switch (action.type) {
					case CD:
						debug("CD '%s'", action.name);

						// Get hashed dir (for validation)
						HashedEntry hSubdir = dirStack.getLast().getEntry(action.name);
						if (hSubdir == null || hSubdir.getType() != HashedEntry.Type.DIR) {
							throw new IOException("Unknown hashed dir: " + action.name);
						}
						dirStack.add((HashedDir) hSubdir);

						// Resolve dir
						dir = dir.resolve(action.name);
						break;
					case GET:
						debug("GET '%s'", action.name);

						// Get hashed file (for validation)
						HashedEntry hFile = dirStack.getLast().getEntry(action.name);
						if (hFile == null || hFile.getType() != HashedEntry.Type.FILE) {
							throw new IOException("Unknown hashed file: " + action.name);
						}

						// Resolve and write file
						Path file = dir.resolve(action.name);
						try (InputStream fileInput = IOHelper.newInput(file)) {
							IOHelper.transfer(fileInput, output.stream);
						}
						break;
					case CD_BACK:
						debug("CD ..");

						// Remove from hashed dir stack
						dirStack.removeLast();
						if (dirStack.isEmpty()) {
							throw new IOException("Empty hDir stack");
						}

						// Get parent
						dir = dir.getParent();
						break;
					case FINISH:
						break loop;
					default:
						throw new AssertionError(String.format("Unsupported action type: '%s'", action.type.name()));
				}
			}

			// Flush all actions
			output.flush();
		}

		// So we've updated :)
	}
}
