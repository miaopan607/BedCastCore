package dev.bedcast.provider;
@FunctionalInterface
public interface ProviderEventSink { void accept(ProviderEvent event); }
