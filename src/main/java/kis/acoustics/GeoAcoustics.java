package kis.acoustics;

import static java.lang.Math.sin;
import static java.lang.Math.cos;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.stream.Stream;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 *
 * @author naoki
 */
public class GeoAcoustics {
    @Value
    static class Point3D{
        double x, y, z;
        
        Point3D turny(double rad) {
            double s = sin(rad);
            double c = cos(rad);
            return new Point3D(x * c - z * s, y, x * s + z * c);
        }
        Point3D move(double dx, double dy, double dz) {
            return new Point3D(x + dx, y + dy, z + dz);
        }
    }
    @Value
    static class Point2D{
        double x, y;
    }
    static abstract class Surface{
        abstract void draw(Graphics2D g, Point2D[] points);
    }

    @Value
    @EqualsAndHashCode(callSuper = true)
    static class Rectangle extends Surface {
        int ul, ur, br, bl;

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
    
    static Point3D[] points = {
        new Point3D( 0, 0,  0), // 0
        new Point3D(10, 0,  0), // 1
        new Point3D(10, 7,  0), // 2
        new Point3D( 0, 7,  0), // 3
        new Point3D( 0, 0, 10), // 4
        new Point3D(10, 0, 10), // 5
        new Point3D(10, 7, 10), // 6
        new Point3D( 0, 7, 10), // 7
    };
    
    static Surface[] surfaces = {
        new Rectangle(0, 1, 2, 3),
        new Rectangle(1, 0, 4, 5),
        new Rectangle(5, 4, 7, 6),
        new Rectangle(6, 7, 3, 2),
        new Rectangle(2, 1, 5, 6),
        new Rectangle(0, 3, 7, 4)
    };
    
    
    public static void main(String[] args) {
        JFrame frame = new JFrame("Hall");
        
        Point2D[] ps = Stream.of(points)
                .map(GeoAcoustics::trans)
                .toArray(Point2D[]::new);

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
    
    static Point2D trans(Point3D p) {
        Point3D t = p.move(-5, -12.5, 0).turny(1/4.);
        int pers = 70;
        int zoom = 30;
        return new Point2D(t.x * zoom * (pers - t.z) / pers + 200,
                -t.y * zoom *(pers - t.z) / pers - 40);
    }
}
