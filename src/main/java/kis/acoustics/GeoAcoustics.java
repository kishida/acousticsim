package kis.acoustics;

import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.function.Function;
import java.util.stream.Stream;
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
        void draw(Graphics2D g, Function<Vec, Point2D> t) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
        void draw(Graphics2D g, Function<Vec, Point2D> t) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

    }

    @Value
    static final class SoundRay {
        Ray ray;
        double intensity;
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

        public Rectangle(Vec ul, Vec ur, Vec br, Vec bl, Material material) {
            super(ul, material);
            this.ul = ul;
            this.ur = ur;
            this.br = br;
            this.bl = bl;
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
        double intersect(Ray y, Surface[] robj) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }
    }
    
    static Vec[] points = {
        new Vec( 0, 0,  0), // 0
        new Vec(10, 0,  0), // 1
        new Vec(10, 7,  0), // 2
        new Vec( 0, 7,  0), // 3
        new Vec( 0, 0, 10), // 4
        new Vec(10, 0, 10), // 5
        new Vec(10, 7, 10), // 6
        new Vec( 0, 7, 10), // 7
    };
    
    static Surface[] surfaces = {
        new Rectangle(points[0], points[1], points[2], points[3], Material.CONCRETE),
        new Rectangle(points[1], points[0], points[4], points[5], Material.WOOD_FLOOR),
        new Rectangle(points[5], points[4], points[7], points[6], Material.CONCRETE),
        new Rectangle(points[6], points[7], points[3], points[2], Material.CARPET_PILE),
        new Rectangle(points[2], points[1], points[5], points[6], Material.CONCRETE),
        new Rectangle(points[0], points[3], points[7], points[4], Material.CONCRETE)
    };
    
    
    public static void main(String[] args) {
        JFrame frame = new JFrame("Hall");
        
        Point2D[] ps = Stream.of(points)
                .map(GeoAcoustics::trans)
                .toArray(Point2D[]::new);

        Vec source = new Vec(3, 2, 3);
        SoundRay ray = new SoundRay(new Ray(source, source.add(new Vec(1, 1, 1).normalize())), 1);
        
        BufferedImage img = new BufferedImage(400, 350, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        Stream.of(surfaces).forEach(s -> s.draw(g, GeoAcoustics::trans));
        
        JLabel label = new JLabel(new ImageIcon(img));
        frame.add(label);
        
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 400);
        frame.setVisible(true);
        
    }
    
    static Vec offset = new Vec(-5, -12.5, 0);
    static Point2D trans(Vec p) {
        Vec t = p.add(offset).turny(1/4.);
        int pers = 70;
        int zoom = 30;
        return new Point2D(t.x * zoom * (pers - t.z) / pers + 220,
                -t.y * zoom *(pers - t.z) / pers - 70);
    }
}
