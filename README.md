# java-async-await
java-async-await是一个async/await库，实现了无栈"协程"最为显著的特性：以同步的方式编写异步代码。

受启发于Kotlin Coroutines与JavaScript Babel项目，通过注解处理器在编译阶段，将async/await方法转换为状态机实现。

### 示例：
使用@Async标记方法，在方法内可通过await "同步" 获取Mono结果（编译后为异步获取）
* exampleA：使用await顺序方式获取结果
```java
import com.zimatars.async.Async;
import reactor.core.publisher.Mono;
import static com.zimatars.async.Z.await;

public class ZTest {

    /**
     * Use the @Async annotation to mark methods that require await
     */
    @Async
    public Mono<Void> exampleA() {
        Mono<Integer> mono = Mono.just(1);

        //just need await(Mono<T> t), the result can be used in a sequential manner
        Integer a = await(mono);
        System.out.println("result:" + a);

        //final return
        return Mono.empty();
    }
    
}
```

* exampleB：对 空Mono 也可使用null判断
```java
public class ZTest {

    @Async
    public Mono<Integer> exampleB() {
        Mono<Integer> mono = Mono.empty();

        //await mono result
        Integer b = await(mono);

        //even if mono is empty, it can also be handled
        if (b == null) {
            b = 0;
        }

        return Mono.just(b);
    }
    
}
```

* exampleC：在if语句中也可使用await
```java
public class ZTest {

    @Async
    public Mono<Integer> exampleC() {
        Integer a = await(Mono.just(1));
        Integer b = 0;

        if (a > 0) {
            //await in if statements
            b = await(Mono.just(1));
        }

        return Mono.just(a + b);
    }
    
}
```
### 编译后状态机实现:
```java

   //exampleA
    switch (next) {
        case 0:
            mono = Mono.just(1);
            next = 1;
            return mono;
        case 1:
            a = (Integer)sent;
            System.out.println("result:" + a);
            next = -1;
            return Mono.empty();
        default:
            throw new IllegalStateException("illegal case value");
    }

    //exampleB
    switch (next) {
        case 0:
            mono = Mono.empty();
            next = 1;
            return mono;
        case 1:
            b = (Integer)sent;
            if (b == null) {
                b = 0;
            }

            next = -1;
            return Mono.just(b);
        default:
            throw new IllegalStateException("illegal case value");
    }
   
    //exampleC
    while(true) {
        switch (next) {
            case 0:
                next = 1;
                return Mono.just(1);
            case 1:
                a = (Integer)sent;
                b = 0;
                if (a <= 0) {
                    next = 3;
                    break;
                }
                next = 4;
                return Mono.just(1);
            case 2:
                next = -1;
                return Mono.just(a + b);
            case 3:
                next = 2;
                break;
            case 4:
                b = (Integer)sent;
                next = 2;
                break;
            default:
                throw new IllegalStateException("illegal case value");
        }
    }

```

### 编译
```
$ mvn clean install
```
### Requirement
* jdk = 8
* Project Reactor

### TODO
* [ ] await in for/while loop block
* [ ] await with try catch block

