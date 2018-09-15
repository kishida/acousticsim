package kis.acoustics;

import java.awt.BorderLayout;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.IntStream;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 *
 * @author naoki
 */
public class GeoAcoustics {

    private static final double GAMMA = 2.2;
    private static final double RECIP_GAMMA = 1 / GAMMA;
    private static final double EPS = 1e-4;
    private static final double INF = 1e20;

    @AllArgsConstructor
    static final class Vec {
        static final Vec UNIT_X = new Vec(1, 0, 0);
        static final Vec UNIT_Y = new Vec(0, 1, 0);
        static final Vec ZERO = new Vec(0, 0, 0);

        final double x, y, z;

        static Vec ofRandom(Random r) {
            return new Vec(r.nextGaussian(),
                           r.nextGaussian(),
                           r.nextGaussian()).normalize();
        }
        
        Vec add(Vec b) {
            return new Vec(x + b.x, y + b.y, z + b.z);
        }

        Vec sub(Vec b) {
            return new Vec(x - b.x, y - b.y, z - b.z);
        }

        Vec mul(double b) {
            return new Vec(x * b, y * b, z * b);
        }

        Vec vecmul(Vec b) {
            return new Vec(x * b.x, y * b.y, z * b.z);
        }

        Vec normalize() {
            double dist = distant();
            if (dist == 0) {
                return UNIT_X;
            }
            return new Vec(x / dist, y / dist, z / dist);
        }

        double distant() {
            return sqrt(x * x + y * y + z * z);
        }

        double dot(Vec b) {
            return x * b.x + y * b.y + z * b.z;
        } // cross:

        Vec mod(Vec b) {
            return new Vec(y * b.z - z * b.y, z * b.x - x * b.z, x * b.y - y * b.x);
        }
        Vec turny(double rad) {
            double s = sin(rad);
            double c = cos(rad);
            return new Vec(x * c - z * s, y, x * s + z * c);
        }
    }    

    @Value
    static final class Ray {
        final Vec obj, dist;

    }

    @AllArgsConstructor
    static abstract class Surface{
        final Vec pos;
        Material material;

        abstract void draw(Graphics2D g, Function<Vec, Point2D> t);
        abstract double intersect(Ray y, Surface[] robj);
        abstract Vec getNormal(Vec point);
    }
    
    static final class Sphere extends Surface {

        final double rad;       // radius

        public Sphere(double rad, Vec p, Material mat) {
            super(p, mat);
            this.rad = rad;
        }

        @Override
        double intersect(Ray r, Surface[] robj) { // returns distance, 0 if nohit
            Vec op = pos.sub(r.obj); // Solve t^2*d.d + 2*t*(o-p).d + (o-p).(o-p)-R^2 = 0
            double t,
                    b = op.dot(r.dist),
                    det = b * b - op.dot(op) + rad * rad;
            if (det < 0) {
                return 0;
            }
            det = sqrt(det);
            robj[0] = this;
            return (t = b - det) > EPS ? t : ((t = b + det) > EPS ? t : 0);
        }

        @Override
        Vec getNormal(Vec point) {
            return point.sub(pos).normalize();
        }
        
        @Override
        void draw(Graphics2D g, Function<Vec, Point2D> t) {
            var p = t.apply(pos);
            var r = size(pos, rad);
            g.drawOval((int)(p.x - r), (int)(p.y - r), (int)r * 2, (int)r * 2);
        }
        
    }

    static class Polygon extends Surface {
        final Vec p1, p3;
        final Vec normal;
        final Vec e1, e2;

        public Polygon(Vec p1, Vec p2, Vec p3, Material mat) {
            super(p2, mat);
            this.p1 = p1;
            this.p3 = p3;
            e1 = p1.sub(pos);
            e2 = p3.sub(pos);
            normal = e1.mod(e2).normalize();
        }
        private double det(Vec v1, Vec v2, Vec v3) {
            return v1.x * v2.y * v3.z + v2.x * v3.y * v1.z + v3.x * v1.y * v2.z
                    -v1.x * v3.y * v2.z - v2.x * v1.y * v3.z - v3.x * v2.y * v1.z;
        }
        @Override
        double intersect(Ray y, Surface[] robj) {
            Vec ray = y.dist.mul(-1);
            double deno = det(e1, e2, ray);
            if (deno <= 0) {
                return 0;
            }
            
            Vec d = y.obj.sub(pos);
            double u = det(d, e2, ray) / deno;
            if (u < 0 || u > 1) {
                return 0;
            }
            double v = det(e1, d, ray) / deno;
            if (v < 0 || u + v > 1) {
                return 0;
            }
            double t = det(e1, e2, d) / deno;
            if (t < 0) {
                return 0;
            }
            robj[0] = this;
            return t;
        }

        @Override
        Vec getNormal(Vec point) {
            return normal;
        }
        
        @Override
        void draw(Graphics2D g, Function<Vec, Point2D> t) {
            var tp1 = t.apply(p1);
            var tp2 = t.apply(p3);
            var tpos = t.apply(pos);
            g.drawLine((int) tp1.x,  (int)tp1.y,  (int)tp2.x,  (int) tp2.y);
            g.drawLine((int) tp2.x,  (int)tp2.y,  (int)tpos.x, (int) tpos.y);
            g.drawLine((int) tpos.x, (int)tpos.y, (int)tp1.x,  (int) tp1.y);
        }

    }

    @Value
    static final class SoundRay {
        Ray ray;
        double intensity;
        double distance;
    }
    
    @Value
    static class Point2D{
        double x, y;
    }
    
    enum Material {
        //            125Hz 250Hz 500Hz 1KHz  2KHz  4KHz
        CONCRETE     (0.01, 0.01, 0.02, 0.02, 0.03, 0.04),
        WALL_CLOTH   (0.03, 0.03, 0.03, 0.04, 0.06, 0.08),
        WOOD_FLOOR   (0.16, 0.14, 0.11, 0.08, 0.08, 0.07),
        CARPET_PUNCH (0.03, 0.04, 0.06, 0.10, 0.20, 0.35),
        CARPET_PILE  (0.09, 0.10, 0.20, 0.25, 0.30, 0.40),
        CURTAIN_FLAT (0.05, 0.07, 0.13, 0.22, 0.32, 0.35),
        CURTAIN_PLEAT(0.10, 0.25, 0.55, 0.65, 0.70, 0.70),
        RELRECTOR    (0.20, 0.13, 0.10, 0.07, 0.06, 0.06)
        ;
        
        double[] absorptions;

        private Material(double... absorptions) {
            this.absorptions = absorptions;
        }
    }
    
    static class Rectangle extends Surface {
        Vec ul, ur, br, bl;
        Polygon p1, p2;

        public Rectangle(Vec ul, Vec ur, Vec br, Vec bl, Material material) {
            super(ul, material);
            this.ul = ul;
            this.ur = ur;
            this.br = br;
            this.bl = bl;
            
            p1 = new Polygon(ul, br, ur, material);
            p2 = new Polygon(ul, bl, br, material);
        }

        @Override
        void draw(Graphics2D g, Function<Vec, Point2D> t) {
            var tul = t.apply(ul);
            var tur = t.apply(ur);
            var tbl = t.apply(bl);
            var tbr = t.apply(br);
            g.drawLine((int)tul.x, (int)tul.y,
                       (int)tur.x, (int)tur.y);
            g.drawLine((int)tur.x, (int)tur.y,
                       (int)tbr.x, (int)tbr.y);
            g.drawLine((int)tbr.x, (int)tbr.y,
                       (int)tbl.x, (int)tbl.y);
            g.drawLine((int)tbl.x, (int)tbl.y,
                       (int)tul.x, (int)tul.y);
        }

        @Override
        Vec getNormal(Vec point) {
            return p1.getNormal(point);
        }
        
        @Override
        double intersect(Ray y, Surface[] robj) {
            double ret = p1.intersect(y, robj);
            if (ret != 0) {
                return ret;
            }
            return p2.intersect(y, robj);
        }
    }
    
    static final Vec[] points = {
        new Vec( 0, 0,  0), // 0
        new Vec(10, 0,  0), // 1
        new Vec(10, 7,  0), // 2
        new Vec( 0, 7,  0), // 3
        new Vec( 0, 0, 10), // 4
        new Vec(10, 0, 10), // 5
        new Vec(10, 7, 10), // 6
        new Vec( 0, 7, 10), // 7
    };
    
    static final List<Surface> surfaces = List.of(
        new Rectangle(points[0], points[1], points[2], points[3], Material.CONCRETE),
        new Rectangle(points[1], points[0], points[4], points[5], Material.WOOD_FLOOR),
        new Rectangle(points[5], points[4], points[7], points[6], Material.CONCRETE),
        new Rectangle(points[6], points[7], points[3], points[2], Material.CARPET_PILE),
        new Rectangle(points[2], points[1], points[5], points[6], Material.CONCRETE),
        new Rectangle(points[0], points[3], points[7], points[4], Material.CONCRETE)
    );
    @AllArgsConstructor
    static class DoublePair {
        double distance, intensity;

        @Override
        public String toString() {
            return String.format("%.3f(%.3f)", distance, intensity);
        }
        
    }
    
    public static void main(String[] args) {
        JFrame frame = new JFrame("Hall");
        
        Vec source = new Vec(3, 2, 3);
        var mic = new Sphere(.1, new Vec(8, 2, 3), Material.RELRECTOR);
        Random rand = new Random();
        Queue<SoundRay> ray = new ArrayDeque<>();
        IntStream.range(0, 100000).forEach(i -> 
            ray.add(new SoundRay(new Ray(source, Vec.ofRandom(rand)), 1, 0)));
        List<SoundRay> rays = new ArrayList<>();
        var sr = new Surface[1];
        var arrivals = new ArrayList<DoublePair>();
        while(!ray.isEmpty()) {
            var r = ray.poll();
            if (r.distance > 340 * 4) {
                continue;
            }
            var dist = mic.intersect(r.ray, sr);
            if (dist != 0) {
                arrivals.add(new DoublePair(dist + r.distance, r.intensity));
            }
            
            surfaces.stream()
                    .map(s -> {
                        var ret = new Surface[1];
                        double d = s.intersect(r.ray, ret);
                        return new Object[]{d, ret[0]};
                    })
                    .filter(ret -> (double)ret[0] > 0)
                    .sorted((c1, c2) -> ((Double)c1[0]).compareTo((Double)c2[0]))
                    .findFirst()
                    .ifPresent(ret -> {
                        var p = r.ray.obj.add(r.ray.dist.mul((double)ret[0]));
                        var collid = new SoundRay( // 衝突点をもとめる
                            new Ray(r.ray.getObj(), p),
                            r.intensity, (double)ret[0]);
                        rays.add(collid);
                        var s = (Surface) ret[1];
                        var n = (s).getNormal(p);
                        var t = r.intensity * (1 - s.material.absorptions[1]);
                        if (t < 0.01) {
                            return;
                        }
                        ray.offer(new SoundRay(new Ray(p, reflect(r.ray.dist, n)), t,
                                r.distance + (double)ret[0]));
                    });
        }
        
                
        BufferedImage img = new BufferedImage(400, 350, BufferedImage.TYPE_INT_RGB);
        JLabel label = new JLabel(new ImageIcon(img));
        frame.add(label);

        BufferedImage graph = new BufferedImage(400, 100, BufferedImage.TYPE_INT_RGB);
        var lblGraph = new JLabel(new ImageIcon(graph));
        frame.add(BorderLayout.SOUTH, lblGraph);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 400);
        frame.setVisible(true);
        
        new Thread(() -> {
            try {
                for (int i = 20; i < 21; ++i) {
                    double dig = i * Math.PI / 200;
                    Function<Vec, Point2D> t = p -> trans(p, dig);
                    Graphics2D g = img.createGraphics();
                    g.setColor(Color.BLACK);
                    g.fillRect(0, 0, 400, 350);
                    g.setColor(Color.WHITE);
                    surfaces.forEach(s -> s.draw(g, t));
                    
                    rays.stream().limit(20).forEach(rr -> {
                        g.setColor(new Color((float)rr.intensity, 0, 0));
                        var p = t.apply(rr.ray.obj);
                        var p2 = t.apply(rr.ray.dist);
                        g.drawLine((int)p.x, (int)p.y, (int)p2.x, (int)p2.y);
                    });
                    
                    g.setColor(Color.YELLOW);
                    mic.draw(g, t);
                    label.repaint();
                    Thread.sleep(100);
                }
            } catch (InterruptedException ex) {
            }
        }).start();
        System.out.println(arrivals.size());
        Collections.sort(arrivals, (d1, d2) -> Double.compare(d1.distance, d2.distance));
        var echo = new double[500];
        for (var ar : arrivals) {
            var index = (int)(ar.distance / 340 * 100);
            if (index < echo.length) {
                echo[index] += ar.intensity * ar.intensity;
            }
        }
        var g2 = graph.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, 400, 100);
        g2.setColor(Color.BLACK);
        for (int i = 0; i < 400; ++i) {
            g2.drawLine(i, (int)(80 - Math.sqrt(echo[i]) * 20), i, 80);
        }
    }
    
    static Vec offset = new Vec(-5, -12.5, 0);
    static Point2D trans(Vec p, double dig) {
        Vec t = p.add(offset).turny(dig);
        int pers = 70;
        int zoom = 30;
        return new Point2D(t.x * zoom * (pers - t.z) / pers + 220,
                -t.y * zoom *(pers - t.z) / pers - 70);
    }
    static double size(Vec p, double r) {
        Vec t = p.add(offset).turny(20 * Math.PI / 200);
        int pers = 70;
        int zoom = 30;
        return r * zoom * (pers - t.z) / pers;
    }
    static Vec reflect(Vec f, Vec n) {
        return f.add(n.mul(2 * f.mul(-1).dot(n)));
    }
}
