package cn.glfs;

import cn.glfs.apply.config.MyConfig;
import cn.glfs.apply.context.GLFSApplicationContext;
import cn.glfs.apply.service.UserService;

public class GLFSApplication {
    public static void main(String[] args) {
        GLFSApplicationContext glfsApplicationContext = new GLFSApplicationContext(MyConfig.class);
        UserService userService = (UserService)glfsApplicationContext.getBean("userService");
        userService.test();
    }
}
