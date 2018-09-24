/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package kis.acoustics;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;

/**
 *
 * @author naoki
 */
public class LoadEcho {
    public static void main(String[] args) throws Exception {
        var om = new ObjectMapper();
        var map = om.readValue(new File("echo.json"), Map.class);
        System.out.println(map.get("freq"));
        var list = (List<List<Double>>) map.get("echo");
        var echo = clipping(list);
        Arrays.stream(echo)
                .map(d -> Arrays.stream(d).summaryStatistics())
                .forEach(System.out::println);
        
        var img = new BufferedImage(600, 300, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 600, 300);
        for (int i = 0; i < echo.length; ++i) {
            var sum = Arrays.stream(echo[i]).map(Math::sqrt).summaryStatistics();
            var graph = new BufferedImage(
                    (int) sum.getCount(), 100, BufferedImage.TYPE_INT_RGB);
            var g2 = graph.createGraphics();
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, (int) sum.getCount(), 100);
            g2.setColor(Color.BLACK);
            for (int x = 0; x < sum.getCount(); ++x) {
                g2.drawLine(x, (int) (100 - Math.sqrt(echo[i][x]) * 100 / sum.getMax()), x, 100);
            }
            g.drawImage(graph, (i % 2) * 300, (i / 2) * 100, 300, 100, null);
        }

        var f = new JFrame("Echo");
        var lbl = new JLabel(new ImageIcon(img));
        f.add(lbl);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setSize(630, 350);
        f.show();
    }
    
    static double[][] clipping(List<List<Double>> list) {
        return list.stream().map(l -> {
            var ar = l.stream()
                    .mapToDouble(d -> d < .01 ? 0 : d)
                    .toArray();
            var j = ar.length - 1;
            for (; j >= 0; j--) {
                if (ar[j] != 0) break;
            }
            return Arrays.copyOf(ar, j + 1);
        }).toArray(double[][]::new);
    }
}
