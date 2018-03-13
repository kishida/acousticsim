package kis.acoustics;

import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.stream.Stream;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 *
 * @author naoki
 */
public class GeoAcoustics {
    
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
    
    @AllArgsConstructor
    static abstract class Surface{
        Material material;

        abstract void draw(Graphics2D g, Point2D[] points);
    }

    static class Rectangle extends Surface {
        int ul, ur, br, bl;

        public Rectangle(int ul, int ur, int br, int bl, Material material) {
            super(material);
            this.ul = ul;
            this.ur = ur;
            this.br = br;
            this.bl = bl;
        }

        @Override
        void draw(Graphics2D g, Point2D[] points) {
            g.drawLine((int)points[ul].x, (int)points[ul].y,
                       (int)points[ur].x, (int)points[ur].y);
            g.drawLine((int)points[ur].x, (int)points[ur].y,
                       (int)points[br].x, (int)points[br].y);
            g.drawLine((int)points[br].x, (int)points[br].y,
                       (int)points[bl].x, (int)points[bl].y);
            g.drawLine((int)points[bl].x, (int)points[bl].y,
                       (int)points[ul].x, (int)points[ul].y);
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
        new Rectangle(0, 1, 2, 3, Material.CONCRETE),
        new Rectangle(1, 0, 4, 5, Material.WOOD_FLOOR),
        new Rectangle(5, 4, 7, 6, Material.CONCRETE),
        new Rectangle(6, 7, 3, 2, Material.CARPET_PILE),
        new Rectangle(2, 1, 5, 6, Material.CONCRETE),
        new Rectangle(0, 3, 7, 4, Material.CONCRETE)
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
        Stream.of(surfaces).forEach(s -> s.draw(g, ps));
        
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
        return new Point2D(t.x * zoom * (pers - t.z) / pers + 200,
                -t.y * zoom *(pers - t.z) / pers - 40);
    }
}
