package org.radarmc.link.core;

import java.nio.file.Path;

public interface PlatformAdapter {
    RadarLogger logger();

    Path dataDirectory();

    ServerSnapshot snapshot();

    void runOnMainThread(Runnable task);

    void dispatchConsoleCommand(String command);
}
