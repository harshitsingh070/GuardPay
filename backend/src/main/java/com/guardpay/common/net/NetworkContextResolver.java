package com.guardpay.common.net;

import jakarta.servlet.http.HttpServletRequest;

/** Resolves trusted network context for the current request. */
public interface NetworkContextResolver {

  NetworkContext resolve(HttpServletRequest request);
}
