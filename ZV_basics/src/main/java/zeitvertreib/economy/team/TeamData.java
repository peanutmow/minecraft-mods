package zeitvertreib.economy.team;

import net.minecraft.ChatFormatting;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TeamData {
	private static final int BASE_MEMBER_CAPACITY = 2;

	private String name;
	private String colorName;
	private UUID leaderId;
	private final Set<UUID> memberIds;
	private final Map<UUID, Integer> contributions = new HashMap<>();
	private boolean withdrawalsBlockedForAll;
	private final Set<UUID> blockedWithdrawalMembers = new HashSet<>();
	private int withdrawalLimitPercent = 33;
	private long withdrawalLimitIntervalMillis = 5 * 60 * 1000L;
	private final Map<UUID, Long> lastWithdrawalTimes = new HashMap<>();
	private int bankBalance;
	private int level;

	public TeamData(String name, String colorName, UUID leaderId) {
		this(name, colorName, leaderId, java.util.List.of(leaderId), 0, 1, null);
	}

	public TeamData(String name, String colorName, UUID leaderId, Collection<UUID> memberIds, int bankBalance) {
		this(name, colorName, leaderId, memberIds, bankBalance, 1, null);
	}

	public TeamData(String name, String colorName, UUID leaderId, Collection<UUID> memberIds, int bankBalance, int level) {
		this(name, colorName, leaderId, memberIds, bankBalance, level, null);
	}

	public TeamData(String name, String colorName, UUID leaderId, Collection<UUID> memberIds, int bankBalance, int level, Map<UUID, Integer> contributions) {
		this(name, colorName, leaderId, memberIds, bankBalance, level, contributions, false, null, 33, 5 * 60 * 1000L, null);
	}

	public TeamData(String name, String colorName, UUID leaderId, Collection<UUID> memberIds, int bankBalance, int level, Map<UUID, Integer> contributions, boolean withdrawalsBlockedForAll, Set<UUID> blockedWithdrawalMembers) {
		this(name, colorName, leaderId, memberIds, bankBalance, level, contributions, withdrawalsBlockedForAll, blockedWithdrawalMembers, 33, 5 * 60 * 1000L, null);
	}

	public TeamData(String name, String colorName, UUID leaderId, Collection<UUID> memberIds, int bankBalance, int level, Map<UUID, Integer> contributions, boolean withdrawalsBlockedForAll, Set<UUID> blockedWithdrawalMembers, int withdrawalLimitPercent, long withdrawalLimitIntervalMillis, Map<UUID, Long> lastWithdrawalTimes) {
		this.name = name;
		this.colorName = colorName;
		this.leaderId = leaderId;
		this.memberIds = new LinkedHashSet<>();
		if (memberIds != null) {
			for (UUID memberId : memberIds) {
				if (memberId != null) {
					this.memberIds.add(memberId);
				}
			}
		}
		if (leaderId != null) {
			this.memberIds.add(leaderId);
		}
		if (contributions != null) {
			this.contributions.putAll(contributions);
		}
		this.withdrawalsBlockedForAll = withdrawalsBlockedForAll;
		if (blockedWithdrawalMembers != null) {
			this.blockedWithdrawalMembers.addAll(blockedWithdrawalMembers);
		}
		this.withdrawalLimitPercent = Math.max(1, Math.min(100, withdrawalLimitPercent));
		this.withdrawalLimitIntervalMillis = Math.max(1, withdrawalLimitIntervalMillis);
		if (lastWithdrawalTimes != null) {
			this.lastWithdrawalTimes.putAll(lastWithdrawalTimes);
		}
		this.bankBalance = Math.max(0, bankBalance);
		this.level = Math.max(1, level);
	}

	public String name() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String colorName() {
		return colorName;
	}

	public void setColorName(String colorName) {
		this.colorName = colorName;
	}

	public ChatFormatting color() {
		ChatFormatting color = ChatFormatting.getByName(colorName);
		return color != null && color.isColor() ? color : ChatFormatting.WHITE;
	}

	public UUID leaderId() {
		return leaderId;
	}

	public Set<UUID> memberIds() {
		return Collections.unmodifiableSet(memberIds);
	}

	public boolean hasMember(UUID playerId) {
		return memberIds.contains(playerId);
	}

	public void addMember(UUID playerId) {
		memberIds.add(playerId);
	}

	public void removeMember(UUID playerId) {
		memberIds.remove(playerId);
		contributions.remove(playerId);
		blockedWithdrawalMembers.remove(playerId);
	}

	public void transferLeadership(UUID playerId) {
		leaderId = playerId;
		memberIds.add(playerId);
	}

	public int level() {
		return level;
	}

	public void setLevel(int level) {
		this.level = Math.max(1, level);
	}

	public void levelUp() {
		level++;
	}

	public int maxMembers() {
		return BASE_MEMBER_CAPACITY + Math.max(0, level - 1);
	}

	public int maxBankCapacity() {
		return switch (level) {
			case 1 -> 500;
			case 2 -> 1000;
			case 3 -> 2500;
			case 4 -> 5000;
			default -> 5000 * (level - 3);
		};
	}

	public int bankBalance() {
		return bankBalance;
	}

	public int getContribution(UUID playerId) {
		return contributions.getOrDefault(playerId, 0);
	}

	public Map<UUID, Integer> contributions() {
		return Collections.unmodifiableMap(contributions);
	}

	public void adjustContribution(UUID playerId, int amount) {
		contributions.merge(playerId, amount, Integer::sum);
	}

	public boolean areWithdrawalsBlockedForAll() {
		return withdrawalsBlockedForAll;
	}

	public void setWithdrawalsBlockedForAll(boolean blocked) {
		withdrawalsBlockedForAll = blocked;
	}

	public Set<UUID> blockedWithdrawalMembers() {
		return Collections.unmodifiableSet(blockedWithdrawalMembers);
	}

	public void blockWithdrawalForPlayer(UUID playerId) {
		if (playerId != null && !playerId.equals(leaderId)) {
			blockedWithdrawalMembers.add(playerId);
		}
	}

	public void unblockWithdrawalForPlayer(UUID playerId) {
		blockedWithdrawalMembers.remove(playerId);
	}

	public int withdrawalLimitPercent() {
		return withdrawalLimitPercent;
	}

	public void setWithdrawalLimitPercent(int percent) {
		this.withdrawalLimitPercent = Math.max(1, Math.min(100, percent));
	}

	public int withdrawalLimitIntervalMinutes() {
		return (int) (withdrawalLimitIntervalMillis / 60_000L);
	}

	public void setWithdrawalLimitIntervalMinutes(int minutes) {
		this.withdrawalLimitIntervalMillis = Math.max(1, minutes) * 60_000L;
	}

	public long lastWithdrawalTime(UUID playerId) {
		return lastWithdrawalTimes.getOrDefault(playerId, 0L);
	}

	public long withdrawCooldownRemaining(UUID playerId, long now) {
		if (playerId == null || playerId.equals(leaderId)) {
			return 0L;
		}
		long remaining = lastWithdrawalTimes.getOrDefault(playerId, 0L) + withdrawalLimitIntervalMillis - now;
		return Math.max(0L, remaining);
	}

	public int maxWithdrawAmountForPlayer(UUID playerId) {
		if (playerId == null || playerId.equals(leaderId)) {
			return bankBalance;
		}
		int limit = Math.max(1, (int) ((long) bankBalance * withdrawalLimitPercent / 100L));
		return Math.min(bankBalance, limit);
	}

	public boolean canWithdrawFromBank(UUID playerId, int amount, long now) {
		if (amount <= 0 || amount > bankBalance) {
			return false;
		}
		if (isWithdrawalsRestricted(playerId)) {
			return false;
		}
		if (playerId == null || playerId.equals(leaderId)) {
			return true;
		}
		if (withdrawCooldownRemaining(playerId, now) > 0L) {
			return false;
		}
		return amount <= maxWithdrawAmountForPlayer(playerId);
	}

	public void recordWithdrawalTime(UUID playerId, long timestamp) {
		if (playerId != null) {
			lastWithdrawalTimes.put(playerId, timestamp);
		}
	}

	public Map<UUID, Long> lastWithdrawalTimes() {
		return Collections.unmodifiableMap(lastWithdrawalTimes);
	}

	public boolean isWithdrawalsRestricted(UUID playerId) {
		if (playerId == null || playerId.equals(leaderId)) {
			return false;
		}
		return withdrawalsBlockedForAll || blockedWithdrawalMembers.contains(playerId);
	}

	public void depositToBank(int amount) {
		bankBalance = Math.max(0, bankBalance + amount);
	}

	public boolean canWithdrawFromBank(int amount) {
		return amount > 0 && bankBalance >= amount;
	}

	public void setBankBalance(int amount) {
		bankBalance = Math.max(0, amount);
	}

	public void withdrawFromBank(int amount) {
		bankBalance = Math.max(0, bankBalance - amount);
	}
}