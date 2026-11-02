package io.github.maharjanronish.jumpjump.core;

/** Minimal mutable 3-component vector (pure Java, no Android dependencies). */
public final class Vec3 {
    public float x, y, z;

    public Vec3() { this(0, 0, 0); }

    public Vec3(float x, float y, float z) { set(x, y, z); }

    public Vec3(Vec3 o) { set(o); }

    public Vec3 set(float x, float y, float z) { this.x = x; this.y = y; this.z = z; return this; }

    public Vec3 set(Vec3 o) { x = o.x; y = o.y; z = o.z; return this; }

    public Vec3 add(Vec3 o) { x += o.x; y += o.y; z += o.z; return this; }

    public Vec3 addScaled(Vec3 dir, float s) { x += dir.x * s; y += dir.y * s; z += dir.z * s; return this; }

    public float dot(Vec3 o) { return x * o.x + y * o.y + z * o.z; }

    public float len() { return (float) Math.sqrt(x * x + y * y + z * z); }

    public float dist(Vec3 o) {
        float dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Signed offset of this point along the progression axis from origin. */
    public float alongU(Vec3 origin) {
        return (x - origin.x) * Config.U_X + (z - origin.z) * Config.U_Z;
    }

    @Override public String toString() { return "(" + x + ", " + y + ", " + z + ")"; }
}
