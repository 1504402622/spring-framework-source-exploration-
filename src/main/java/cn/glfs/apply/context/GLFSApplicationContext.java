package cn.glfs.apply.context;

import cn.glfs.apply.annotions.Component;
import cn.glfs.apply.annotions.ComponentScan;
import cn.glfs.apply.annotions.Lazy;
import cn.glfs.apply.annotions.Scope;
import cn.glfs.apply.bean.BeanDefinition;
import cn.glfs.apply.enums.ScopeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class GLFSApplicationContext {

    private static final Logger logger = LoggerFactory.getLogger(GLFSApplicationContext.class);
    private final Map<String, BeanDefinition> beanDefinitionMap = new HashMap<>();

    public GLFSApplicationContext(Class<?> configClass) {
        try {
            List<Class<?>> classes = scanBean(configClass);
            loadBeanMetadata(classes);
            logger.debug("Application context initialized successfully with [{}] beans", beanDefinitionMap.size());
        } catch (Exception e) {
            logger.error("Failed to initialize application context", e);
            throw new RuntimeException("Application context initialization failed", e);
        }
    }

    private void loadBeanMetadata(List<Class<?>> classes) {
        for (Class<?> clazz : classes) {
            try {
                Component componentAnnotation = clazz.getAnnotation(Component.class);
                String beanName = getBeanName(componentAnnotation, clazz);
                BeanDefinition beanDefinition = createBeanDefinition(clazz);
                beanDefinitionMap.put(beanName, beanDefinition);
                logger.debug("Registered bean: [{}] with scope: [{}]", beanName, beanDefinition.getScope());
            } catch (Exception e) {
                logger.error("Failed to load bean metadata for class:[{}]", clazz.getName(), e);
            }
        }
    }

    /**
     * 创建Bean定义
     */
    private BeanDefinition createBeanDefinition(Class<?> clazz) {
        BeanDefinition beanDefinition = new BeanDefinition();
        beanDefinition.setBeanClass(clazz);

        // 处理Scope注解
        ScopeType scope = resolveScope(clazz);
        beanDefinition.setScope(scope);

        // 单例且非懒加载的Bean立即创建
        if (ScopeType.SINGLETON.equals(scope) && !isLazy(clazz)) {
            Object beanInstance = createBean(clazz);
            beanDefinition.setBeanObject(beanInstance);
        }

        return beanDefinition;
    }

    /**
     * 创建Bean实例
     */
    private Object createBean(Class<?> clazz) {
        try {
            logger.debug("Creating bean instance for class: [{}]", clazz.getName());
            //
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            logger.error("Failed to create bean instance for class: [{}]", clazz.getName(), e);
            throw new RuntimeException("Bean creation failed for: " + clazz.getName(), e);
        }
    }


    /**
     * 判断是否为懒加载
     */
    private boolean isLazy(Class<?> clazz) {
        return clazz.isAnnotationPresent(Lazy.class);
    }

    /**
     * 解析Bean的作用域
     */
    private ScopeType resolveScope(Class<?> clazz) {
        if (clazz.isAnnotationPresent(Scope.class)) {
            String value = clazz.getAnnotation(Scope.class).value();
            return ScopeType.ofValue(value);
        }
        return ScopeType.SINGLETON; // 默认单例
    }


    private String getBeanName(Component componentAnnotation, Class<?> clazz) {
        String beanName = componentAnnotation.value();
        if (beanName.trim().isEmpty()) {
            // 默认使用类名首字母小写
            String className = clazz.getSimpleName();
            beanName = Character.toLowerCase(className.charAt(0)) + className.substring(1);
        }
        return beanName;
    }

    /**
     * 根据配置类扫描bean获取所有bean的class
     */
    private List<Class<?>> scanBean(Class<?> configClass) {
        List<Class<?>> classes = new ArrayList<>();

        if (!configClass.isAnnotationPresent(ComponentScan.class)) {
            logger.debug("Config class [{}] is not annotated with @ComponentScan", configClass.getName());
            return classes;
        }

        ComponentScan componentScan = configClass.getAnnotation(ComponentScan.class);
        String basePackage = componentScan.value();

        if (basePackage.isEmpty()) {
            basePackage = configClass.getPackage().getName();
            logger.debug("Using default base package: [{}]", basePackage);
        }

        logger.debug("Scanning for components in package: [{}]", basePackage);

        try {
            Set<Class<?>> scannedClasses = scanPackage(basePackage);
            classes.addAll(scannedClasses);
            logger.debug("Found [{}] component classes in package: [{}]", scannedClasses.size(), basePackage);
        } catch (Exception e) {
            logger.error("Failed to scan package: [{}]", basePackage, e);
            throw new RuntimeException("Component scan failed for package: " + basePackage, e);
        }

        return classes;
    }

    /**
     * 扫描指定包路径下的所有类
     */
    private Set<Class<?>> scanPackage(String basePackage) {
        Set<Class<?>> classes = new LinkedHashSet<>();
        String packagePath = basePackage.replace('.', '/');

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = this.getClass().getClassLoader();
        }

        try {
            // 拿到所有模块的cn/glfs/service路径
            Enumeration<URL> resources = classLoader.getResources(packagePath);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                scanResource(resource, basePackage, classes, classLoader);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan package resources: " + basePackage, e);
        }

        return classes;
    }

    /**
     * 扫描单个资源
     */
    private void scanResource(URL resource, String basePackage, Set<Class<?>> classes, ClassLoader classLoader) {
        String protocol = resource.getProtocol();

        // jar中的文件和文件系统中的文件
        if ("file".equals(protocol)) {
            // 文件系统（开发环境）
            scanFileSystem(resource, basePackage, classes, classLoader);
        } else if ("jar".equals(protocol)) {
            // JAR文件（生产环境）
            scanJarFile(resource, basePackage, classes, classLoader);
        } else {
            logger.debug("Unsupported resource protocol: {} for package: {}", protocol, basePackage);
        }
    }


    /**
     * 扫描文件系统目录
     */
    private void scanFileSystem(URL resource, String basePackage, Set<Class<?>> classes, ClassLoader classLoader) {
        try {
            File directory = new File(resource.getFile());
            if (!directory.exists() || !directory.isDirectory()) {
                logger.debug("Directory does not exist or is not a directory: {}", directory.getAbsolutePath());
                return;
            }

            scanDirectory(directory, basePackage, classes, classLoader);
        } catch (Exception e) {
            logger.error("Failed to scan file system directory: {}", resource.getFile(), e);
        }
    }


    /**
     * 递归扫描目录
     */
    private void scanDirectory(File directory, String currentPackage, Set<Class<?>> classes, ClassLoader classLoader) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                // 递归扫描子目录
                String subPackage = currentPackage + "." + file.getName();
                scanDirectory(file, subPackage, classes, classLoader);
            } else if (file.getName().endsWith(".class")) {
                // 处理类文件
                processClassFile(file, currentPackage, classes, classLoader);
            }
        }
    }

    /**
     * 处理类文件
     */
    private void processClassFile(File classFile, String currentPackage, Set<Class<?>> classes, ClassLoader classLoader) {
        String fileName = classFile.getName();
        String className = currentPackage + '.' + fileName.substring(0, fileName.length() - 6);

        try {
            Class<?> clazz = classLoader.loadClass(className);
            if (isComponentClass(clazz)) {
                classes.add(clazz);
                logger.debug("Found component class: {}", className);
            }
        } catch (ClassNotFoundException e) {
            logger.debug("Class not found: {}", className, e);
        } catch (NoClassDefFoundError e) {
            logger.debug("Skipping class due to missing dependencies: {}", className);
        } catch (Exception e) {
            logger.debug("Error loading class: {}", className, e);
        }
    }


    /**
     * 扫描JAR文件
     */
    private void scanJarFile(URL resource, String basePackage, Set<Class<?>> classes, ClassLoader classLoader) {
        try {
            JarURLConnection jarConn = (JarURLConnection) resource.openConnection();
            try (JarFile jarFile = jarConn.getJarFile()) {
                Enumeration<JarEntry> entries = jarFile.entries();
                String packagePath = basePackage.replace('.', '/') + "/";

                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String entryName = entry.getName();

                    if (entryName.startsWith(packagePath) && entryName.endsWith(".class") && !entry.isDirectory()) {
                        processJarClassEntry(entryName, basePackage, classes, classLoader);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Failed to scan JAR file: {}", resource, e);
        }
    }

    /**
     * 处理JAR中的类条目
     */
    private void processJarClassEntry(String entryName, String basePackage, Set<Class<?>> classes, ClassLoader classLoader) {
        // 转换路径为类名：cn/glfs/apply/Test.class -> cn.glfs.apply.Test
        String className = entryName.substring(0, entryName.length() - 6).replace('/', '.');

        try {
            Class<?> clazz = classLoader.loadClass(className);
            if (isComponentClass(clazz)) {
                classes.add(clazz);
                logger.debug("Found component class in JAR: {}", className);
            }
        } catch (ClassNotFoundException e) {
            logger.debug("Class not found in JAR: {}", className, e);
        } catch (NoClassDefFoundError e) {
            logger.debug("Skipping class in JAR due to missing dependencies: {}", className);
        } catch (Exception e) {
            logger.debug("Error loading class from JAR: {}", className, e);
        }
    }

    /**
     * 判断是否为组件类
     */
    private boolean isComponentClass(Class<?> clazz) {
        return clazz.isAnnotationPresent(Component.class) &&
                !clazz.isInterface() &&
                !clazz.isAnnotation() &&
                !Modifier.isAbstract(clazz.getModifiers());
    }

    /**
     * 根据名获得Bean
     */
    public Object getBean(String beanName) {
        if (!beanDefinitionMap.containsKey(beanName)) {
            throw new RuntimeException("Bean not found: " + beanName);
        }

        BeanDefinition beanDefinition = beanDefinitionMap.get(beanName);
        ScopeType scope = beanDefinition.getScope();

        if (ScopeType.SINGLETON.equals(scope)) {
            return getSingletonBean(beanDefinition);
        } else if (ScopeType.PROTOTYPE.equals(scope)) {
            return createBean(beanDefinition.getBeanClass());
        } else {
            throw new IllegalStateException("Unknown scope: " + scope);
        }
    }

    /**
     * 根据类型获取Bean
     */
    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> requiredType) {
        for (BeanDefinition beanDefinition : beanDefinitionMap.values()) {
            if (requiredType.isAssignableFrom(beanDefinition.getBeanClass())) {
                return (T) getBean(getBeanNameFromClass(beanDefinition.getBeanClass()));
            }
        }
        throw new RuntimeException("No bean found of type: " + requiredType.getName());
    }

    /**
     * 根据类获取Bean名称
     */
    private String getBeanNameFromClass(Class<?> clazz) {
        Component component = clazz.getAnnotation(Component.class);
        if (component != null) {
            String beanName = component.value();
            if (!beanName.trim().isEmpty()) {
                return beanName;
            }
        }
        String className = clazz.getSimpleName();
        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
    }


    /**
     * 获取单例Bean
     */
    private Object getSingletonBean(BeanDefinition beanDefinition) {
        synchronized (beanDefinition) {
            if (beanDefinition.getBeanObject() == null) {
                Object bean = createBean(beanDefinition.getBeanClass());
                beanDefinition.setBeanObject(bean);
            }
            return beanDefinition.getBeanObject();
        }
    }
}
