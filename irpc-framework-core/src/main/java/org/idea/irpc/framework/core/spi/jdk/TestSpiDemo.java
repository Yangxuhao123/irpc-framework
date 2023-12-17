package org.idea.irpc.framework.core.spi.jdk;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * @Author linhao
 * @Date created in 2:50 下午 2022/2/4
 */
public class TestSpiDemo {

    public static void doTest(ISpiTest iSpiTest){
        System.out.println("begin");
        iSpiTest.doTest();
        System.out.println("end");
    }

    public static void main(String[] args) {
        // JDK内置提供的ServiceLoader会自动帮助我们去加载
        // /META-INF/services/目录下边的文件，并且将其转换为具体实现类。
        ServiceLoader<ISpiTest> serviceLoader = ServiceLoader.load(ISpiTest.class);
        Iterator<ISpiTest> iSpiTestIterator = serviceLoader.iterator();
        while (iSpiTestIterator.hasNext()){
            ISpiTest iSpiTest = iSpiTestIterator.next();
            TestSpiDemo.doTest(iSpiTest);
        }
    }
}
