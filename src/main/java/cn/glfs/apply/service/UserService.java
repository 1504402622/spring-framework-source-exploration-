package cn.glfs.apply.service;

import cn.glfs.apply.annotions.Component;
import cn.glfs.apply.annotions.Lazy;
import cn.glfs.apply.annotions.Scope;

@Component
@Scope("singleton")
@Lazy
public class UserService {
    public void test() {
        System.out.println("UserService.testSuccess!");
    }
}
