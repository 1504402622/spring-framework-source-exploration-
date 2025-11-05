package cn.glfs.apply.service;

import cn.glfs.apply.annotions.Component;
import cn.glfs.apply.annotions.Scope;

@Component
@Scope("prototype")
public class OrderService {
    public void test() {
        System.out.println("OrderService.testSuccess!");
    }
}
