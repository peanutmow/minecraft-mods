package zeitvertreib.economy.team;

import net.minecraft.ChatFormatting;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class TeamData {
	private static final int BASE_MEMBER_CAPACITY = 2;

	private final String name;
	private final String colorName;
	private UUID leaderId;
	private final Set<UUID> memberIds;
	private int bankBalance;
	private int level;

	public TeamData(String name, String colorName, UUID leaderId) {
		this(name, colorName, leaderId, java.util.List.of(leaderId), 0, 1);
	}

	public TeamData(String name, String colorName, UUID leaderId, Collection<UUID> memberIds, int bankBalance) {
		this(name, colorName, leaderId, memberIds, bankBalance, 1);
	}

	public TeamData(String name, String colorName, UUID leaderId, Collection<UUID> memberIds, int bankBalance, int level) {
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
		this.bankBalance = Math.max(0, bankBalance);
		this.level = Math.max(1, level);
	}

	public String name() {
		return name;
	}

	public String colorName() {
		return colorName;
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

	public int bankBalance() {
		return bankBalance;
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