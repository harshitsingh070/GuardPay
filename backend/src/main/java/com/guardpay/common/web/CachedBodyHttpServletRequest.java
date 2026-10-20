package com.guardpay.common.web;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Reads the request body once so the logging filter can show it and Spring MVC
 * can still deserialize it. Only used for textual bodies; everything else is
 * streamed through untouched.
 */
final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

  private final byte[] body;

  CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
    super(request);
    this.body = request.getInputStream().readAllBytes();
  }

  byte[] body() {
    return body;
  }

  String bodyAsString() {
    return new String(body, charset());
  }

  @Override
  public ServletInputStream getInputStream() {
    ByteArrayInputStream source = new ByteArrayInputStream(body);
    return new ServletInputStream() {
      @Override
      public int read() {
        return source.read();
      }

      @Override
      public int read(byte[] buffer, int offset, int length) {
        return source.read(buffer, offset, length);
      }

      @Override
      public boolean isFinished() {
        return source.available() == 0;
      }

      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public void setReadListener(ReadListener listener) {
        throw new UnsupportedOperationException("Asynchronous reads are not supported");
      }
    };
  }

  @Override
  public BufferedReader getReader() {
    return new BufferedReader(new InputStreamReader(getInputStream(), charset()));
  }

  private Charset charset() {
    String encoding = getCharacterEncoding();
    try {
      return encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
    } catch (Exception unsupported) {
      return StandardCharsets.UTF_8;
    }
  }
}
