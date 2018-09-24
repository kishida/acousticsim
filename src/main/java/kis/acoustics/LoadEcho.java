/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package kis.acoustics;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
        Arrays.stream(echo).map(d -> d.length).forEach(System.out::println);
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
