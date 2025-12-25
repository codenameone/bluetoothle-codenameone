package com.codename1.hellocodenameone;

import com.codename1.ui.Display;
import com.codename1.ui.Form;
import com.codename1.ui.Label;
import com.codename1.ui.layouts.BorderLayout;
import com.codename1.ui.plaf.UIManager;
import com.codename1.ui.util.Resources;
import com.codename1.system.Lifecycle;

public class HelloCodenameOne extends Lifecycle {
    @Override
    public void runApp() {
        Form hi = new Form("Hello World", new BorderLayout());
        hi.add(BorderLayout.CENTER, new Label("Hello Codename One"));
        hi.show();
    }
}
