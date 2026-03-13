package com.traffic.map;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * Service to download OpenStreetMap data natively into the application
 * using the Overpass API. This avoids downloading gigabytes of country-wide
 * PBF files and eliminates the need for external tools like Osmium.
 */
@Service
public class OsmFetcher {

    private static final String OVERPASS_API = "https://overpass-api.de/api/interpreter";
    
    // Default: GS Road, Guwahati (small slice to test)
    // format: minLat, minLon, maxLat, maxLon
    private static final double MIN_LAT = 26.135;
    private static final double MIN_LON = 91.785;
    private static final double MAX_LAT = 26.155;
    private static final double MAX_LON = 91.805;

    private final HttpClient httpClient;

    public OsmFetcher() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Fetch a specific bounding box from Overpass and save it to the specified file.
     * Only fetches roads (highway=*) and their associated nodes to keep the file lightweight.
     */
    public String fetchMapData(double minLat, double minLon, double maxLat, double maxLon, String outputPath) throws IOException, InterruptedException {
        Path outPath = Paths.get(outputPath);
        
        // If we already downloaded it recently, just return the cached file
        if (Files.exists(outPath) && Files.size(outPath) > 1000) {
            System.out.println("Using cached OSM map: " + outputPath);
            return outPath.toString();
        }

        System.out.println("Downloading fresh OSM map directly from Overpass API...");
        
        // Overpass QL query:
        // [out:xml][bbox:minLat,minLon,maxLat,maxLon];
        // ( way["highway"]; node(w); );
        // out body;
        String query = String.format(
            "[out:xml][bbox:%f,%f,%f,%f];(way[\"highway\"];node(w););out body;",
            minLat, minLon, maxLat, maxLon
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OVERPASS_API))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/x-www-form-urlencoded")
                // Overpass expects "data=..." in POST body
                .POST(HttpRequest.BodyPublishers.ofString("data=" + query))
                .build();

        HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(outPath));

        if (response.statusCode() == 200) {
            System.out.println("Map downloaded successfully: " + outputPath);
            return outPath.toString();
        } else {
            throw new IOException("Failed to download map from Overpass API. HTTP Status: " + response.statusCode());
        }
    }
    
    /**
     * Download the default Guwahati zone.
     */
    public String fetchDefaultGuwahatiMap(String outputPath) throws IOException, InterruptedException {
        return fetchMapData(MIN_LAT, MIN_LON, MAX_LAT, MAX_LON, outputPath);
    }
}
