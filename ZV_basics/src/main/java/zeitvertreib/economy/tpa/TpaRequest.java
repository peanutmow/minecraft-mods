package zeitvertreib.economy.tpa;

import java.util.UUID;

public record TpaRequest(
	UUID requesterId,
	UUID targetId,
	String requesterName,
	String targetName,
	TpaType type,
	long expiresAtMillis
) {
	public static final long REQUEST_TIMEOUT_MILLIS = 60_000L;

	public enum TpaType {
		/** Requester teleports to target. Sent via /tpa [player]. */
		GOTO,
		/** Target teleports to requester. Sent via /tpahere [player]. */
		COME
	}
}
