package zeitvertreib.economy.home;

public record PlayerHome(
    String name,
    String dimension,
    double x,
    double y,
    double z,
    float yRot,
    float xRot
) {}
