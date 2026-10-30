package org.radarmc.link.core.joins;

import java.util.List;
import org.radarmc.link.core.api.ApiException;
import org.radarmc.link.core.api.JoinEntry;

@FunctionalInterface
public interface JoinSender {
    int send(List<JoinEntry> joins) throws ApiException;
}
