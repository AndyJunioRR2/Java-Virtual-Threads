package org.example;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

public class URLProcessor implements Runnable {
    public final String url; // visible para el Main
    private static final int TIMEOUT = 15000;
    private static final int MAX_RETRIES = 2;
    private static final String USER_AGENT = "Mozilla/5.0 (...)";
    private static final Set<String> URL_SHORTENERS = Set.of(
            "bit.ly", "goo.gl", "tinyurl.com", "t.co", "ow.ly", "is.gd",
            "buff.ly", "adf.ly", "bitly.com", "trib.al", "ift.tt", "fb.me",
            "rb.gy", "tiny.cc", "cutt.ly", "youtu.be"
    );

    private int resultado = -1; // -1 = sin procesar o error

    public URLProcessor(String url) {
        if (url.contains("twitter.com") || url.contains("x.com")) {
            this.url = url.replace("x.com", "twitter.com");
        } else {
            this.url = url;
        }
    }

    @Override
    public void run() {
        try {
            this.resultado = contarEnlacesInternos();
        } catch (Exception e) {
            System.err.println("Error procesando URL " + url + ": " + e.getMessage());
            this.resultado = 0; // o mantener -1 como señal de error
        }
    }

    public int getResultado() {
        return resultado;
    }

    public int contarEnlacesInternos() throws IOException, URISyntaxException {
        if (!isValidUrl(url)) throw new IOException("URL inválida: " + url);

        String urlResuelta = url;
        try {
            String dominio = obtenerDominio(url);
            if (dominio != null && esAcortadorURL(dominio)) {
                urlResuelta = resolverRedireccion(url);
                System.out.println("URL resuelta: " + url + " -> " + urlResuelta);
            }
        } catch (Exception e) {
            System.err.println("Redirección fallida: " + e.getMessage());
        }

        String dominio = obtenerDominio(urlResuelta);
        if (dominio == null) throw new IOException("Dominio no válido");

        Document doc = null;
        IOException lastException = null;

        for (int i = 0; i <= MAX_RETRIES; i++) {
            try {
                doc = Jsoup.connect(urlResuelta)
                        .userAgent(USER_AGENT)
                        .timeout(TIMEOUT)
                        .followRedirects(true)
                        .ignoreHttpErrors(false)
                        .ignoreContentType(true)
                        .get();
                break;
            } catch (IOException e) {
                lastException = e;
                try {
                    Thread.sleep(1000 * (i + 1));
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        if (doc == null) throw lastException;

        Elements enlaces = doc.select("a[href]");
        Set<String> enlacesInternos = new HashSet<>();
        for (Element enlace : enlaces) {
            String href = enlace.attr("abs:href").trim();
            if (!href.isEmpty()) {
                try {
                    String enlaceDominio = obtenerDominio(href);
                    if (enlaceDominio != null && enlaceDominio.equals(dominio)) {
                        enlacesInternos.add(href);
                    }
                } catch (URISyntaxException ignored) {}
            }
        }

        return enlacesInternos.size();
    }

    private String resolverRedireccion(String urlStr) throws IOException, URISyntaxException {
        URL url = new URL(urlStr);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setConnectTimeout(TIMEOUT);
        connection.setReadTimeout(TIMEOUT);

        int status = connection.getResponseCode();
        if (status == 404 || status == 500) {
            throw new IOException("Error HTTP: " + status);
        }

        if (status >= 300 && status < 400) {
            String location = connection.getHeaderField("Location");
            if (location != null) {
                if (location.startsWith("/")) {
                    URL base = new URL(urlStr);
                    return new URL(base.getProtocol() + "://" + base.getHost() + location).toString();
                } else {
                    return location;
                }
            }
        }

        return urlStr;
    }

    private boolean esAcortadorURL(String dominio) {
        return URL_SHORTENERS.contains(dominio);
    }

    private String obtenerDominio(String urlStr) throws URISyntaxException {
        URI uri = new URI(urlStr);
        String host = uri.getHost();
        return host != null && host.startsWith("www.") ? host.substring(4) : host;
    }

    private boolean isValidUrl(String urlStr) {
        try {
            URI uri = new URI(urlStr);
            return urlStr.startsWith("http://") || urlStr.startsWith("https://");
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
