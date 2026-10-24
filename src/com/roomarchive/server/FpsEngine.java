/** 3D 枪战核心引擎 v2：体素世界（OpenGL ES 渲染用）+ 玩家/敌人逻辑 + 射线判定 */
package com.roomarchive.server;

import java.util.ArrayList;
import java.util.List;

public class FpsEngine {

    // ===== 世界 =====
    public static final int W = 16, D = 16, H = 4;
    public static final byte AIR = 0, STONE = 1, GRASS = 2, DIRT = 3, BRICK = 4, WOOD = 5, CRATE = 6, AMMO = 7, LEAF = 8;
    public byte[] blocks = new byte[W * D * H];

    // ===== 玩家 =====
    public float px = 11.5f, pz = 2.5f, pa = (float) Math.PI;   // 位置 + 朝向角
    public float pitch = 0;           // 俯仰角（弧度，正=抬头）
    public float pitchVel = 0;        // 后坐力上抬速度
    public int hp = 100;
    public int kills = 0;
    public float recoil = 0;
    public int ammo = 30, clipSize = 30;   // 弹药
    public float reloadTime = 0;           // >0 表示换弹中

    public static class Sprite {
        public float x, z, hp = 100;
        public float angle;
        public String name = "";
        public int color;
        public int id;
        public boolean dead;
        public boolean isPlayer;
        public float aiTimer = 0, aiDir = 0, shootTimer = 0;
        public float hitFlash = 0;      // 受击闪烁计时
        public float deathTime = -1;    // 死亡时刻（倒下动画用）
        public float muzzleFlash = 0;   // 敌人枪口闪光
    }

    /** 烟雾粒子：开火枪口烟 */
    public static class Smoke {
        public float x, y, z;
        public float life;
        public float maxLife;
        public float size;
    }

    public List<Smoke> smokes = new ArrayList<>();

    /** 弹孔贴花：打墙留下的痕迹 */
    public static class WallHit {
        public float x, y, z;
        public float nx, nz;
        public float life;
    }

    public List<WallHit> wallHits = new ArrayList<>();

    /** 子弹：可见弹道 + 命中火花 */
    public static class Bullet {
        public float sx, sz;        // 起点
        public float x, z;          // 当前位置
        public float tx, tz;        // 终点
        public float progress = 0;  // 0..1 飞行进度
        public float impactLife = 0;// >0 表示命中火花剩余时间
        public boolean hitTarget;   // 是否命中敌人
    }

    public List<Sprite> sprites = new ArrayList<>();
    public List<Bullet> bullets = new ArrayList<>();

    public FpsEngine() {
        genWorld();
        resetLocal();
    }

    // ===== 世界生成 =====
    public void genWorld() {
        java.util.Random r = new java.util.Random(20261017);
        for (int i = 0; i < blocks.length; i++) blocks[i] = AIR;
        // 地面：y0 石底，y1 草地
        for (int gx = 0; gx < W; gx++)
            for (int gz = 0; gz < D; gz++) {
                set(gx, 0, gz, STONE);
                set(gx, 1, gz, r.nextInt(10) == 0 ? DIRT : GRASS);
            }
        // 边界石墙 y0..3
        for (int gx = 0; gx < W; gx++)
            for (int y = 0; y < H; y++) {
                set(gx, y, 0, STONE);
                set(gx, y, D - 1, STONE);
            }
        for (int gz = 0; gz < D; gz++)
            for (int y = 0; y < H; y++) {
                set(0, y, gz, STONE);
                set(W - 1, y, gz, STONE);
            }
        // 内部掩体：砖墙 / 木柱 / 平台
        int[][] walls = {
                {4, 3}, {6, 8}, {10, 4}, {12, 11}, {7, 12}, {3, 10}, {13, 7}, {9, 9}, {5, 5}, {11, 13},
                {8, 2}, {2, 7}, {13, 2}, {5, 13}, {10, 10}
        };
        int idx = 0;
        for (int[] p : walls) {
            if (idx % 3 == 0) {
                // 砖墙 2 宽高 2
                for (int dx = 0; dx < 2; dx++)
                    for (int dz = 0; dz < 2; dz++)
                        for (int y = 2; y < 4; y++) set(p[0] + dx, y, p[1] + dz, BRICK);
            } else if (idx % 3 == 1) {
                // 木柱高 3
                for (int y = 2; y < 4; y++) set(p[0], y, p[1], WOOD);
            } else {
                // 平台 3x1 高 2
                for (int dx = 0; dx < 3; dx++)
                    for (int dz = 0; dz < 1; dz++)
                        for (int y = 2; y < 3; y++) set(p[0] + dx, y, p[1] + dz, BRICK);
            }
            idx++;
        }
        // 弹药箱/铁箱道具
        int[][] crates = {{6, 4}, {9, 12}, {13, 9}, {4, 13}, {8, 6}, {11, 7}, {3, 6}, {14, 3}};
        for (int i = 0; i < crates.length; i++) {
            int[] cp = crates[i];
            if (i % 2 == 0) {
                set(cp[0], 2, cp[1], CRATE);
            } else {
                set(cp[0], 2, cp[1], AMMO);
                set(cp[0], 3, cp[1], AMMO);
            }
        }
        // 树木：树干 + 树叶冠
        int[][] trees = {{2, 3}, {13, 13}, {7, 4}, {11, 12}, {4, 8}, {9, 2}, {14, 6}, {3, 12}};
        for (int[] tp : trees) {
            int tx = tp[0], tz = tp[1];
            if (get(tx, 2, tz) != AIR) continue;
            set(tx, 2, tz, WOOD);
            set(tx, 3, tz, LEAF);
            set(tx + 1, 3, tz, LEAF);
            set(tx - 1, 3, tz, LEAF);
            set(tx, 3, tz + 1, LEAF);
            set(tx, 3, tz - 1, LEAF);
        }
        // 出生点确保空地
        clearAround(11, 2);
        clearAround(2, 12);
    }

    private void clearAround(int gx, int gz) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                int x = gx + dx, z = gz + dz;
                if (x < 1 || z < 1 || x >= W - 1 || z >= D - 1) continue;
                set(x, 2, z, AIR);
                set(x, 3, z, AIR);
            }
    }

    public void set(int x, int y, int z, byte v) {
        if (x < 0 || y < 0 || z < 0 || x >= W || y >= H || z >= D) return;
        blocks[y * W * D + z * W + x] = v;
    }

    public byte get(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= W || y >= H || z >= D) return STONE;
        return blocks[y * W * D + z * W + x];
    }

    /** 玩家可碰撞的实心判定（y1/y2/y3 层） */
    public boolean solidAt(int gx, int gz) {
        if (gx < 0 || gz < 0 || gx >= W || gz >= D) return true;
        return get(gx, 1, gz) != AIR || get(gx, 2, gz) != AIR || get(gx, 3, gz) != AIR;
    }

    public void resetLocal() {
        px = 11.5f;
        pz = 2.5f;
        pa = (float) Math.PI;
        pitch = 0;
        pitchVel = 0;
        hp = 100;
        kills = 0;
        ammo = clipSize;
        reloadTime = 0;
    }

    /** 俯仰角更新：后坐力上抬 + 自动回正（每帧调用） */
    public void updatePitch(float dt) {
        pitch += pitchVel;
        pitchVel *= 0.86f;
        // 自动回正
        pitch -= pitch * Math.min(1f, dt * 6f);
        // 限制范围
        if (pitch > 0.55f) pitch = 0.55f;
        if (pitch < -0.55f) pitch = -0.55f;
    }

    /** 开始换弹 */
    public void startReload() {
        if (ammo >= clipSize || reloadTime > 0 || hp <= 0) return;
        reloadTime = 1.25f;
    }

    /** 换弹计时（在 updateBullets 中推进） */
    public void tickReload(float dt) {
        if (reloadTime > 0) {
            reloadTime -= dt;
            if (reloadTime <= 0) {
                reloadTime = 0;
                ammo = clipSize;
            }
        }
    }

    public void resetSprites(boolean single) {
        sprites.clear();
        if (single) {
            int[] ex = {3, 8, 12, 5};
            int[] ez = {6, 8, 11, 11};
            int[] ec = {0xFFE53935, 0xFFFB8C00, 0xFF8E24AA, 0xFF1E88E5};
            for (int i = 0; i < 4; i++) {
                Sprite s = new Sprite();
                s.x = ex[i] + 0.5f;
                s.z = ez[i] + 0.5f;
                s.hp = 100;
                s.color = ec[i];
                s.id = i;
                s.name = "敌军" + (i + 1);
                sprites.add(s);
            }
        }
    }

    // ===== 移动 =====
    public void move(float fwd, float turn, float dt) {
        if (hp <= 0) return;
        pa += turn * dt * 3.0f;
        float speed = fwd * 3.4f * dt;
        float nx = px + (float) Math.cos(pa) * speed;
        float nz = pz + (float) Math.sin(pa) * speed;
        if (!solidAt((int) nx, (int) nz)) {
            px = nx;
            pz = nz;
        }
    }

    // ===== 射线 =====
    /** 步进射线：返回撞墙距离（玩家视线高度 y=1.6，撞 y1/y2/y3 层） */
    public float castDist(float x, float z, float ang) {
        float dx = (float) Math.cos(ang), dz = (float) Math.sin(ang);
        for (float d = 0.05f; d < 64; d += 0.05f) {
            int gx = (int) (x + dx * d);
            int gz = (int) (z + dz * d);
            if (solidAt(gx, gz)) return d;
        }
        return 64;
    }

    public boolean lineBlocked(float x1, float z1, float x2, float z2) {
        float ang = (float) Math.atan2(z2 - z1, x2 - x1);
        float d = (float) Math.sqrt((x2 - x1) * (x2 - x1) + (z2 - z1) * (z2 - z1));
        return castDist(x1, z1, ang) < d - 0.3f;
    }

    // ===== 弹壳 =====
    public static class Casing {
        public float x, z, y, vx, vz, vy, rot;
        public float life = 3.0f;
    }

    public final List<Casing> casings = new ArrayList<>();

    private void updateCasings(float dt) {
        for (int i = casings.size() - 1; i >= 0; i--) {
            Casing k = casings.get(i);
            k.vy -= 11f * dt;
            k.x += k.vx * dt;
            k.z += k.vz * dt;
            k.y += k.vy * dt;
            k.rot += 14f * dt;
            if (k.y < 0.10f && k.vy < 0) {
                k.y = 0.10f;
                k.vy = -k.vy * 0.32f;
                k.vx *= 0.45f;
                k.vz *= 0.45f;
                if (k.vy < 0.25f) {
                    k.vy = 0;
                    k.vx = 0;
                    k.vz = 0;
                }
            }
            k.life -= dt;
            if (k.life <= 0) casings.remove(i);
        }
    }

    /** 开火：命中射线路径上最近的目标（AABB 判定），生成可见子弹 */
    public Sprite fire() {
        recoil = 1f;
        pitchVel += 0.07f;   // 后坐力上抬
        if (hp <= 0) return null;
        if (ammo <= 0 || reloadTime > 0) return null;   // 空仓/换弹中不能开火
        ammo--;
        float wallD = castDist(px, pz, pa);
        float dx = (float) Math.cos(pa), dz = (float) Math.sin(pa);
        Sprite best = null;
        float bestD = 20f;
        float maxD = Math.min(wallD, 20f);
        for (float d = 0.1f; d < maxD; d += 0.06f) {
            float X = px + dx * d, Z = pz + dz * d;
            for (Sprite s : sprites) {
                if (s.dead) continue;
                if (Math.abs(X - s.x) < 0.45f && Math.abs(Z - s.z) < 0.45f && d < bestD) {
                    best = s;
                    bestD = d;
                }
            }
        }
        // 子弹：从枪口到命中点（敌人距离或墙距离）
        float startX = px + dx * 0.3f;
        float startZ = pz + dz * 0.3f;
        float endD = best != null ? bestD : Math.min(wallD, 20f);
        if (endD < 0.5f) endD = 0.5f;
        Bullet b = new Bullet();
        b.sx = startX;
        b.sz = startZ;
        b.x = startX;
        b.z = startZ;
        b.tx = px + dx * endD;
        b.tz = pz + dz * endD;
        b.hitTarget = best != null;
        bullets.add(b);
        // 打墙弹孔贴花
        if (best == null && wallD < 20f) {
            float hx = px + dx * wallD;
            float hz = pz + dz * wallD;
            if (hx > 0 && hx < W && hz > 0 && hz < D) {
                WallHit wh = new WallHit();
                wh.x = hx;
                wh.y = 1.6f;
                wh.z = hz;
                wh.nx = Math.abs(dx) > 0.3f ? (dx > 0 ? 1 : -1) : 0;
                wh.nz = Math.abs(dz) > 0.3f ? (dz > 0 ? 1 : -1) : 0;
                if (wh.nx == 0 && wh.nz == 0) wh.nz = 1;
                wh.life = 6f;
                wallHits.add(wh);
            }
        }
        // 抛弹壳（向右侧飞出，落地弹跳）
        float rdx = (float) Math.cos(pa + Math.PI / 2), rdz = (float) Math.sin(pa + Math.PI / 2);
        Casing k = new Casing();
        k.x = px + rdx * 0.35f;
        k.z = pz + rdz * 0.35f;
        k.y = 1.35f;
        k.vx = dx * 0.5f + rdx * 1.7f;
        k.vz = dz * 0.5f + rdz * 1.7f;
        k.vy = 2.5f;
        casings.add(k);
        // 枪口烟雾
        Smoke sm = new Smoke();
        sm.x = px + dx * 0.35f;
        sm.y = 1.5f;
        sm.z = pz + dz * 0.35f;
        sm.life = 0.5f;
        sm.maxLife = 0.5f;
        sm.size = 0.22f;
        smokes.add(sm);
        if (best != null) {
            best.hp -= 50;
            best.hitFlash = 0.25f;
            if (best.hp <= 0 && !best.dead) {
                best.dead = true;
                best.deathTime = 0;
                kills++;
                // 击杀烟雾
                Smoke ks = new Smoke();
                ks.x = best.x;
                ks.y = 1.4f;
                ks.z = best.z;
                ks.life = 0.7f;
                ks.maxLife = 0.7f;
                ks.size = 0.5f;
                smokes.add(ks);
            }
        }
        return best;
    }

    /** 子弹/烟雾/特效计时 */
    public void updateBullets(float dt) {
        // 换弹推进
        tickReload(dt);
        // 弹壳物理
        updateCasings(dt);
        // 死亡倒地动画推进
        for (Sprite s : sprites) {
            if (s.dead) s.deathTime += dt;
        }
        for (int i = bullets.size() - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            if (b.progress < 1f) {
                b.progress += dt / 0.22f;
                if (b.progress >= 1f) {
                    b.progress = 1f;
                    b.x = b.tx;
                    b.z = b.tz;
                    b.impactLife = b.hitTarget ? 0.45f : 0.3f;
                } else {
                    b.x = b.sx + (b.tx - b.sx) * b.progress;
                    b.z = b.sz + (b.tz - b.sz) * b.progress;
                }
            } else if (b.impactLife > 0) {
                b.impactLife -= dt;
                if (b.impactLife <= 0) bullets.remove(i);
            } else {
                bullets.remove(i);
            }
        }
        for (int i = smokes.size() - 1; i >= 0; i--) {
            Smoke s = smokes.get(i);
            s.life -= dt;
            s.y += dt * 0.55f;
            s.size += dt * 0.22f;
            if (s.life <= 0) smokes.remove(i);
        }
        for (int i = wallHits.size() - 1; i >= 0; i--) {
            wallHits.get(i).life -= dt;
            if (wallHits.get(i).life <= 0) wallHits.remove(i);
        }
    }

    /** 波次刷怪：n 个敌人在玩家周围随机刷出 */
    public void spawnWave(int n) {
        sprites.clear();
        java.util.Random r = new java.util.Random();
        int[] cols = {0xFFE53935, 0xFFFB8C00, 0xFF8E24AA, 0xFF1E88E5};
        for (int i = 0; i < n; i++) {
            Sprite s = new Sprite();
            float ang = (float) (r.nextDouble() * Math.PI * 2);
            float dist = 4.5f + (float) r.nextDouble() * 4.5f;
            s.x = px + (float) Math.cos(ang) * dist;
            s.z = pz + (float) Math.sin(ang) * dist;
            if (s.x < 1.2f) s.x = 1.2f;
            if (s.x > W - 1.2f) s.x = W - 1.2f;
            if (s.z < 1.2f) s.z = 1.2f;
            if (s.z > D - 1.2f) s.z = D - 1.2f;
            s.hp = 100;
            s.color = cols[i % 4];
            s.id = i;
            s.name = "敌军" + (i + 1);
            sprites.add(s);
        }
    }

    // ===== AI =====
    public void updateAi(float dt) {
        for (Sprite s : sprites) {
            if (s.dead || s.isPlayer) continue;
            s.hitFlash -= dt;
            if (s.muzzleFlash > 0) s.muzzleFlash -= dt;
            s.aiTimer -= dt;
            s.shootTimer -= dt;
            float dx = px - s.x, dz = pz - s.z;
            float dist = (float) Math.sqrt(dx * dx + dz * dz);
            boolean sees = dist < 13f && !lineBlocked(s.x, s.z, px, pz);
            if (sees) {
                s.angle = (float) Math.atan2(dz, dx);
                if (dist > 0.9f) {
                    float nx = s.x + (float) Math.cos(s.angle) * 1.7f * dt;
                    float nz = s.z + (float) Math.sin(s.angle) * 1.7f * dt;
                    if (!solidAt((int) nx, (int) nz)) {
                        s.x = nx;
                        s.z = nz;
                    }
                }
                if (s.shootTimer <= 0 && dist < 11f) {
                    s.shootTimer = 1.1f;
                    s.muzzleFlash = 0.12f;
                    float da = (float) Math.abs(normalize((float) Math.atan2(pz - s.z, px - s.x) - s.angle));
                    if (da < 0.13f && !lineBlocked(s.x, s.z, px, pz)) {
                        hp -= 14;
                        if (hp < 0) hp = 0;
                    }
                }
            } else {
                if (s.aiTimer <= 0) {
                    s.aiTimer = 0.8f + (float) Math.random() * 1.4f;
                    s.angle += (float) ((Math.random() - 0.5) * 2.0);
                }
                float nx = s.x + (float) Math.cos(s.angle) * 1.1f * dt;
                float nz = s.z + (float) Math.sin(s.angle) * 1.1f * dt;
                if (!solidAt((int) nx, (int) nz)) {
                    s.x = nx;
                    s.z = nz;
                } else {
                    s.angle += (float) (Math.PI / 2);
                }
            }
        }
    }

    public boolean allEnemiesDead() {
        for (Sprite s : sprites) {
            if (!s.dead && !s.isPlayer) return false;
        }
        return true;
    }

    static float normalize(float a) {
        while (a > Math.PI) a -= (float) (2 * Math.PI);
        while (a < -Math.PI) a += (float) (2 * Math.PI);
        return a;
    }
}
