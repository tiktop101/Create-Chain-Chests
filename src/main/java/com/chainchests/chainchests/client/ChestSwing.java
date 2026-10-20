package com.chainchests.chainchests.client;

public final class ChestSwing {
    private static final float GRAVITY = 0.08f;
    private static final float LENGTH = 0.9f;
    private static final float DAMPING = 0.12f;
    private static final float ACCEL_GAIN = 3.0f;
    private static final float MAX_ACCEL = 0.03f;
    private static final float MAX_ANGLE = (float) Math.toRadians(55);

    private float angle;
    private float prevAngle;
    private float velocity;
    private float lastSpeed;
    private boolean hasLastSpeed;

    public void tick(float chainSpeed) {
        prevAngle = angle;

        float accel = hasLastSpeed ? (chainSpeed - lastSpeed) : 0f;

        accel = Math.max(-MAX_ACCEL, Math.min(MAX_ACCEL, accel));
        lastSpeed = chainSpeed;
        hasLastSpeed = true;

        float angularAccel = -(GRAVITY / LENGTH) * (float) Math.sin(angle)
                - DAMPING * velocity
                - (accel * ACCEL_GAIN / LENGTH) * (float) Math.cos(angle);

        velocity += angularAccel;
        angle += velocity;

        if (angle > MAX_ANGLE) {
            angle = MAX_ANGLE;
            velocity = 0f;
        } else if (angle < -MAX_ANGLE) {
            angle = -MAX_ANGLE;
            velocity = 0f;
        }
    }

    public float angle(float partialTick) {
        return prevAngle + (angle - prevAngle) * partialTick;
    }
}
