package quasar.test;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.Suspendable;
import co.paralleluniverse.strands.SuspendableCallable;
import rx.Single;

public class Test {

	@Suspendable
	public static <T> T await(Single<T> single) throws SuspendExecution{
		Fiber thisFiber = Fiber.currentFiber();
		Throwable[] xRet = new Throwable[1];
		Object[] vRet = new Object[1];
		single.subscribe(ret -> {
				vRet[0] = ret;
				System.err.println("Unparking Fiber on value");
				thisFiber.unpark();
			},
			x -> {
				xRet[0] = x;
				System.err.println("Unparking Fiber on exception");
				thisFiber.unpark();
			});
		System.err.println("Fiber parking for await");
		Fiber.park();
		System.err.println("Fiber unparked from await");
		if(xRet[0] != null){
			if(xRet[0] instanceof RuntimeException)
				throw (RuntimeException)xRet[0];
			throw new RuntimeException(xRet[0]);
		}
		return (T) vRet[0];
	}

	public static <T> Single<T> fiber(SuspendableCallable<T> body){
		return Single.<T>create(sub -> {
			System.err.println("Creating Fiber");
			Fiber<T> fiber = new Fiber<T>(() -> {
				try{
					System.err.println("Running Fiber");
					T ret = body.run();
					System.err.println("Fiber done");
					if(!sub.isUnsubscribed())
						sub.onSuccess(ret);
				}catch(Throwable x){
					System.err.println("Fiber failed");
					if(!sub.isUnsubscribed())
						sub.onError(x);
				}
			});
			System.err.println("Starting Fiber");
			fiber.start();
		});
	}

	public static void main(String[] args) {
		System.err.println("Before");
		Single<Integer> singleFiber = fiber(() -> {
			System.err.println("In fiber");
			if(true)
				return 2;
			else
				throw new RuntimeException("ouch");
		});
		Single<Integer> secondFiber = fiber(() -> {
			System.err.println("In fiber 2, waiting for fiber 1");
			return await(singleFiber);
		});
		System.err.println("After");
		secondFiber.subscribe(ret -> {
			System.err.println("Got Fiber result: "+ret);
		}, x -> {
			System.err.println("Got Fiber exception: "+x);
		});
		System.err.println("After subscribe");
	}
}
