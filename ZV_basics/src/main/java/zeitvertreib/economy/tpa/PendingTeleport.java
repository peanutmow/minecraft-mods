package zeitvertreib.economy.tpa;

import java.util.UUID;

public final class PendingTeleport {
	public static final long WARMUP_MILLIS = 3_000L;
	private static final double MOVE_THRESHOLD_SQ = 0.01; // ~0.1 blocks

	private final UUID teleportingPlayerId;
	private final UUID destinationPlayerId;
	private final UUID requesterId;
	private final String teleportingPlayerName;
	private final String destinationPlayerName;
	private final int cost;

	private double originX;
	private double originY;
	private double originZ;
	private long executeAtMillis;

	public PendingTeleport(
		UUID teleportingPlayerId,
		UUID destinationPlayerId,
		UUID requesterId,
		String teleportingPlayerName,
		String destinationPlayerName,
		double originX,
		double originY,
		double originZ,
		long executeAtMillis,
		int cost
	) {
		this.teleportingPlayerId = teleportingPlayerId;
		this.destinationPlayerId = destinationPlayerId;
		this.requesterId = requesterId;
		this.teleportingPlayerName = teleportingPlayerName;
		this.destinationPlayerName = destinationPlayerName;
		this.originX = originX;
		this.originY = originY;
		this.originZ = originZ;
		this.executeAtMillis = executeAtMillis;
		this.cost = cost;
	}

	public boolean hasPlayerMoved(double currentX, double currentY, double currentZ) {
		double dx = currentX - originX;
		double dy = currentY - originY;
		double dz = currentZ - originZ;
		return (dx * dx + dy * dy + dz * dz) > MOVE_THRESHOLD_SQ;
	}

	/** Resets the warmup timer and origin to the player's current position. */
	public void reset(double x, double y, double z) {
		this.originX = x;
		this.originY = y;
		this.originZ = z;
		this.executeAtMillis = System.currentTimeMillis() + WARMUP_MILLIS;
	}

	public UUID teleportingPlayerId() { return teleportingPlayerId; }
	public UUID destinationPlayerId() { return destinationPlayerId; }
	public UUID requesterId() { return requesterId; }
	public String teleportingPlayerName() { return teleportingPlayerName; }
	public String destinationPlayerName() { return destinationPlayerName; }
	public double originX() { return originX; }
	public double originY() { return originY; }
	public double originZ() { return originZ; }
	public long executeAtMillis() { return executeAtMillis; }
	public int cost() { return cost; }
}
