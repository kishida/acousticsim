/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package kis.acoustics;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;

/**
 *
 * @author kishida
 */
public class LoadWav {
    public static void main(String[] args) throws Exception {
        // var name = "/Users/ST20197/Downloads/a2002011001-e02.wav";
        var name = "C:\\Users\\naoki\\Dropbox\\harp.wav";
        var file = new File(name);
        try (var is = AudioSystem.getAudioInputStream(file)) {
            var format = is.getFormat();
            System.out.println(format);
            System.out.println(is.getFrameLength() / format.getSampleRate());
            var pathL = new Path2D.Double();
            pathL.moveTo(0, 0);
            var pathR = new Path2D.Double();
            pathR.moveTo(0, 0);
            var image = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB);
            var g = image.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 600, 400);
            g.setColor(Color.BLACK);
            int len = Math.min((int)(format.getSampleRate() * 10), (int)is.getFrameLength());
            var bytes = new byte[format.getFrameSize()];
            var signals = new short[2][10][];
            signals[0][0] = new short[len];
            signals[1][0] = new short[len];
            for (int i = 0; i < len; ++i) {
                if (is.read(bytes) < 0) {
                    System.out.println(i);
                    break;
                }
                var buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
                short right = buffer.getShort();
                short left = buffer.getShort();
                pathL.lineTo(i, left);
                //pathR.lineTo(i, right);
                signals[0][0][i] = left;
                signals[1][0][i] = right;
            }
            
            // wavelet transform
            for (var ch = 0; ch < 2; ++ch) {
                var wlen = len;
                var base = signals[ch][0];
                for (int i = 1; i < signals[ch].length - 1; ++i) {
                    wlen /= 2;
                    signals[ch][i] = new short[wlen];
                    var next = new short[wlen];
                    for (int j = 0; j < wlen; ++j) {
                        signals[ch][i][j] = (short)
                                ((base[j * 2] - base[j * 2 + 1]) / 2);
                        next[j] = (short)((base[j * 2] + base[j * 2 + 1]) / 2);
                    }
                    base = next;
                }
                signals[ch][signals[ch].length - 1] = base;
            }
            
            // delay
            var delay = new double[][]{
                {.1, .5},
                {.2, .2},
                {.3, .1},
            };
            var shifts = Arrays.stream(delay)
                    .mapToDouble(d -> d[0])
                    .mapToInt(t -> (int)(format.getSampleRate() * t))
                    .map(t -> t - (t & 0b111111111))
                    .toArray();
            for (var ch = 0; ch < 2; ++ch) {
                var shift = (int)(format.getSampleRate() * .1); // 0.1秒ディレイ
                shift -= shift & 0b111111111; // 位相ずれが起きないようにする
                for (var i = 1; i < signals[ch].length; ++i) {
                    for (var j = signals[ch][i].length - 1; j >= shift; --j) {
                        signals[ch][i][j] += (short)(signals[ch][i][j - shift] / 2);
                    }
                    shift /= 2;
                }
            }
            
            var result = new short[2][];
            // wavelet invert transform
            for (var ch = 0; ch < 2; ++ch) {
                var last = signals[ch][signals[ch].length - 1];
                for (int i = signals[ch].length - 2; i > 0; --i) {
                    var next = new short[last.length * 2];
                    for (int j = 0; j < last.length; ++j) {
                        next[j * 2] = (short) (last[j] + signals[ch][i][j]);
                        next[j * 2 + 1] = (short) (last[j] - signals[ch][i][j]);
                    }
                    last = next;
                }
                result[ch] = last;
            }

            // write wav
            var out = new ByteArrayOutputStream();
            for (int i = 0; i < result[0].length; ++i) {
                var buf = new byte[4];
                var outbuf = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
                for (int ch = 0; ch < 2; ++ch) {
                    outbuf.putShort(result[ch][i]);
                }
                out.writeBytes(buf);
            }
            var ais = new AudioInputStream(new ByteArrayInputStream(out.toByteArray()),
                    format, len);
            var dataline = new DataLine.Info(Clip.class,format);
            var clip = (Clip) AudioSystem.getLine(dataline);
            clip.open(ais);
            clip.start();
            //clip.close();
            
            // draw
            for (int i = 0; i < result[0].length; ++i) {
                pathR.lineTo(i, result[0][i]);
            }
            
            var sizer = AffineTransform.getScaleInstance(
                    500. / len, -100. / Short.MAX_VALUE);
            var transL = AffineTransform.getTranslateInstance(20, 100);
            transL.concatenate(sizer);
            var transR = AffineTransform.getTranslateInstance(20, 300);
            transR.concatenate(sizer);
            
            pathL.transform(transL);
            pathR.transform(transR);
            g.draw(pathL);
            g.draw(pathR);
            var f = new JFrame("Graph");
            var lbl = new JLabel(new ImageIcon(image));
            f.add(lbl);
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setSize(630, 450);
            f.setVisible(true);
        }
    }
}
