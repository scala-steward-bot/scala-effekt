package effekt
package handler

import internals. { UseI, UseM, EffOp }

trait Handler {
  type G[_]

  type Cont[A, R]
  type Answer[R]
  type CPS[A] = Cont[A, ω] => Answer[ω]

  // G is pointed (would be equiv to providing G[Unit])
  def unit[R]: R => G[R]

  def use[A](body: CPS[A]): I[A]

  private[effekt] def CPS[A, R](body: CPS[A])(g: Cont[A, R]): Answer[R]
}

// I[R] => I[G[R]]
trait Idiomatic extends Handler {
  type Cont[A, R] = I[G[A => R]]
  type Answer[R]  = I[G[R]]

  // For idiomatic handlers G needs to be a functor.
  def map[A, B]: (A => B) => G[A] => G[B]

  // ideally use would be parametric in R (with a rank-2 type).
  def use[A](body: CPS[A]): I[A] =
    UseI(EffOp(this, body), pure(a => a))

  // useEff is a bit more expressive, but this often suffices
  def usePure[A](body: G[A => ω] => G[ω]): I[A] =
    use { _ map body }

  def handle[R](prog: this.type => I[R]): I[G[R]] =
    effekt.handle(this)(prog)

  def apply[R](prog: this.type => I[R]): I[G[R]] =
    effekt.handle(this)(prog)

  // TODO can we somehow return a Monadic[R] here?
  def dynamic[R](prog: this.type => C[R])(run: G[ω] => (ω => C[R]) => C[R]): C[R] =
    effekt.dynamic(this, run)(prog)

  private[effekt] def CPS[A, R](body: CPS[A])(g: Cont[A, R]): Answer[R] =
    body.asInstanceOf[I[G[A => R]] => I[G[R]]](g)
}

// a handler for monadic programs that is itself monadic normal bubble semantics
// C[R] => C[R]
trait Monadic extends Handler {
  type Cont[A, R] = A => C[G[R]]
  type Answer[R]  = C[G[R]]

  def use[A](body: CPS[A]): I[A] = UseM(EffOp(this, body), pure)

  def handle[R](prog: this.type => C[R]): C[G[R]] = effekt.handle(this)(prog)
  def apply[R](prog: this.type => C[R]): C[G[R]] = effekt.handle(this)(prog)

  private[effekt] def CPS[A, R](body: CPS[A])(g: Cont[A, R]): Answer[R] =
    body.asInstanceOf[(A => C[G[R]]) => C[G[R]]](g)
}


// Some other derived handlers

trait Id extends Idiomatic {
  type G[X] = X
  def unit[R] = identity
  def map[A, B] = _.apply

  // since G is Id, we can also run the handler on monadic computations
  def handleMonadic[R](prog: this.type => C[R]): C[R] =
    dynamic(prog) { res => resume => resume(res) }
}