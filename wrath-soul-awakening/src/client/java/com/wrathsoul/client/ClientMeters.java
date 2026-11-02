package com.wrathsoul.client;

import com.wrathsoul.SyncPayload;

public final class ClientMeters {
	private ClientMeters() {}
	public static volatile SyncPayload current = SyncPayload.EMPTY;
}
