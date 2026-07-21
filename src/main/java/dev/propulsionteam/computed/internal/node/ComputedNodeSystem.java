package dev.devce.websnodelib;

/** https://github.com/webyep-art/webs_node_lib (MIT, webyep). */
public final class WebsNodeLib {
    private WebsNodeLib() {}

    public static void bootstrap() {
        dev.devce.websnodelib.internal.InternalNodes.register();
    }

}
