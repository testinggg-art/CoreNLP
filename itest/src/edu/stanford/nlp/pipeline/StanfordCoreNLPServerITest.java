package edu.stanford.nlp.pipeline;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.junit.BeforeClass;
import org.junit.Test;

import org.junit.Assert;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;

/**
 * Test launching a server and running a few random commands
 */
public class StanfordCoreNLPServerITest {

  static StanfordCoreNLPServer server;
  static int port;

  @BeforeClass
  public static void launchServer() throws IOException {
    String[] args = { "-port", "0", "-preload", "tokenize,ssplit,pos,parse" };

    server = StanfordCoreNLPServer.launchServer(args);
    port = server.getServer().get().getAddress().getPort();
    System.out.println("Server running on port " + port);
  }

  @Test
  public void testLive() {
    String result = IOUtils.slurpURLNoExceptions("http://localhost:" + port + "/live");
    Assert.assertNotNull(result);
    Assert.assertEquals("live", result.trim());
  }

  @Test
  public void testReady() {
    String result = IOUtils.slurpURLNoExceptions("http://localhost:" + port + "/ready");
    Assert.assertNotNull(result);
    Assert.assertEquals("ready", result.trim());
  }


  @Test
  public void testClient() {
    String query = "The dog ate a fish";
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,parse");
    StanfordCoreNLPClient client = new StanfordCoreNLPClient(props, "http://localhost", port);
    // if something goes wrong, we don't want the unittest waiting forever for a response
    client.setTimeoutMilliseconds(30 * 1000);
    Annotation annotation = client.process(query);
    Throwable t = annotation.get(CoreAnnotations.ExceptionAnnotation.class);
    Assert.assertNull(t);

    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
    Assert.assertEquals(1, sentences.size());
  }

  @Test
  public void testClientFailure() {
    String query = "The dog ate a fish";
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,parse");
    //StanfordCoreNLPClient client = new StanfordCoreNLPClient(props, "http://localhost", port);
    StanfordCoreNLPClient client = new StanfordCoreNLPClient(props, "localhost", port);
    client.setTimeoutMilliseconds(1000);
    Annotation annotation = client.process(query);
    Throwable t = annotation.get(CoreAnnotations.ExceptionAnnotation.class);
    Assert.assertNotNull(t);
  }

  public String postURL(URL serverURL, byte[] message) throws IOException {
    URLConnection connection = serverURL.openConnection();
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
    connection.setRequestProperty("Accept-Charset", "utf-8");
    connection.setRequestProperty("User-Agent", StanfordCoreNLPServerITest.class.getName());
    connection.setConnectTimeout(30 * 1000);
    connection.setReadTimeout(30 * 1000);
    ((HttpURLConnection) connection).setRequestMethod("POST");

    connection.connect();
    connection.getOutputStream().write(message);
    connection.getOutputStream().flush();
    String response = IOUtils.slurpInputStream(connection.getInputStream(), "utf-8");
    return response;
  }
  
  @Test
  public void testTregexJson() throws IOException {
    String expected="{\"sentences\":[{\"0\": { \"match\": \"(NN dog)\\n\", \"spanString\": \"dog\", \"namedNodes\": [ ] }, \"1\": { \"match\": \"(NN fish)\\n\", \"spanString\": \"fish\", \"namedNodes\": [ ] } } ]}".replaceAll(" ", "");
    
    String query = "The dog ate a fish";
    byte[] message = query.getBytes("utf-8");
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,parse");
    String queryParams = String.format("pattern=NN&properties=%s",
                                       URLEncoder.encode(PropertiesUtils.propsAsJsonString(props), "utf-8"));
    URL serverURL = new URL("http", "localhost", port, "/tregex?" + queryParams);
    String response = postURL(serverURL, message);

    Assert.assertEquals(expected, response.replaceAll(" ", "").replaceAll("\n", ""));
  }
}
