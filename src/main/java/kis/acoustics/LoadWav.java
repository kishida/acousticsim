/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package kis.acoustics;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import javax.sound.sampled.AudioFileFormat;
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
        // var name = "~/Downloads/a2002011001-e02.wav";
        var name = "C:\\Users\\naoki\\Dropbox\\harp.wav";
        var file = new File(name);
        try (var audioInput = AudioSystem.getAudioInputStream(file)) {
            var format = audioInput.getFormat();
            System.out.println(format);
            System.out.println(audioInput.getFrameLength() / format.getSampleRate());
            var pathL = new Path2D.Double();
            pathL.moveTo(0, 0);
            var pathR = new Path2D.Double();
            pathR.moveTo(0, 0);
            var image = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB);
            var g = image.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 600, 400);
            g.setColor(Color.BLACK);
            int len = Math.min((int)(format.getSampleRate() * 10), (int)audioInput.getFrameLength());
            var bytes = new byte[format.getFrameSize()];
            var freqs = 8;
            var signals = new short[2][freqs][];
            signals[0][0] = new short[len];
            signals[1][0] = new short[len];
            for (int i = 0; i < len; ++i) {
                if (audioInput.read(bytes) < 0) {
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
            var startTime = System.currentTimeMillis();
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
            System.out.println("wavlet " + (System.currentTimeMillis() - startTime));
            // delay
            var om = new ObjectMapper();
            var map = om.readValue(new File("echo.json"), Map.class);
            var echoSq = LoadEcho.clipping((List<List<Double>>) map.get("echo"));
            var echo = Arrays.stream(echoSq)
                             .map(ec -> Arrays.stream(ec)
                                              .map(Math::sqrt)
                                              .toArray())
                             .map(ec -> {
                                 var max = Arrays.stream(ec).max().orElse(.01);
                                 return Arrays.stream(ec).map(d -> d / max).toArray();
                             })
                             .toArray(double[][]::new);
            var delayed = new short[2][freqs][];
            var start = 2;
            IntStream.range(0, 2).forEach(ch -> {
                IntStream.range(1, freqs).forEach(f -> {
                    if (f < start) {
                        // delayed[ch][f] = new short[signals[ch][f].length];
                        delayed[ch][f] = Arrays.copyOf(signals[ch][f], signals[ch][f].length);
                        return;
                    }
                    delayed[ch][f] = new short[signals[ch][f].length];
                    var devides = 1000;
                    IntStream.range(0, signals[ch][f].length / devides).parallel().forEach(ib -> {
                        for (int is = 0; is < devides; is++) {
                            var i = ib * devides + is;
                            if (i >= signals[ch][f].length) {
                                break;
                            }
                            double d1 = 0.;
                            for (int j = 0; j < echo[5 - f + start].length; j++) {
                                if (i - j < 0) {
                                    continue;
                                }
                                var s = signals[ch][f][i - j] * echo[5 - f + start][j];
                                d1 += s;
                            }
                            d1 /= 3.7;
                            d1 = (d1 + signals[ch][f][i]) / 2;
                            if (d1 > Short.MAX_VALUE) {
                                d1 = Short.MAX_VALUE;
                            }
                            if (d1 < Short.MIN_VALUE) {
                                d1 = Short.MIN_VALUE;
                            }
                            delayed[ch][f][i] = (short) d1;
                        }
                    });
                });
            });
            System.out.println("delay " + (System.currentTimeMillis() - startTime));
            /*
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
                var start = 1;
                for (int i = 1; i < start; ++i) {
                    shift /= 2;
                }
                for (var i = start; i < signals[ch].length; ++i) {
                    for (var j = signals[ch][i].length - 1; j >= shift; --j) {
                        signals[ch][i][j] += (short)(signals[ch][i][j - shift] / 2);
                    }
                    shift /= 2;
                }
            }
            */
            // wavelet invert transform
            var result = new short[2][];
            for (var ch = 0; ch < 2; ++ch) {
                var last = delayed[ch][delayed[ch].length - 1];
                for (int i = delayed[ch].length - 2; i > 0; --i) {
                    var next = new short[last.length * 2];
                    for (int j = 0; j < last.length; ++j) {
                        next[j * 2] = (short) (last[j] + delayed[ch][i][j]);
                        next[j * 2 + 1] = (short) (last[j] - delayed[ch][i][j]);
                    }
                    last = next;
                }
                result[ch] = last;
            }
            System.out.println("inv wavelet " + (System.currentTimeMillis() - startTime));

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
            // AudioSystem.write(ais, AudioFileFormat.Type.WAVE, new File(file.getParentFile(), "delayed.wav"));
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
            
            var echoGraph = new BufferedImage(600, 300, BufferedImage.TYPE_INT_RGB);
            GeoAcoustics.drawEcho(echoGraph.createGraphics(), echoSq);
            f.add("South", new JLabel(new ImageIcon(echoGraph)));
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setSize(630, 450);
            f.setVisible(true);
        }
    }
}
