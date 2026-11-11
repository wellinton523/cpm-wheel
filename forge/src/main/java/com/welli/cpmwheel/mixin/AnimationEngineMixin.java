package com.welli.cpmwheel.mixin;

import com.tom.cpm.shared.animation.AnimationRegistry;
import com.tom.cpm.shared.animation.AnimationTrigger;
import com.tom.cpm.shared.animation.IPose;
import com.welli.cpmwheel.FirstPerson;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * CPM animates the first-person hand only with "First person hand" pose animations. This adds the
 * layer/value (and optionally gesture) animations of the GLOBAL pose, so the arm shows the same state as in 3rd person.
 * Optional: if CPM's code differs, the redirect simply does not apply (require = 0).
 */
@Mixin(targets = "com.tom.cpm.shared.animation.AnimationEngine", remap = false)
public abstract class AnimationEngineMixin {
    @Redirect(method = "handleAnimation", require = 0, remap = false,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lcom/tom/cpm/shared/animation/AnimationRegistry;getPoseAnimations(Lcom/tom/cpm/shared/animation/IPose;)Ljava/util/List;"))
    private List<AnimationTrigger> cpmwheel$firstPerson(AnimationRegistry reg, IPose pose) {
        return FirstPerson.extend(reg, pose);
    }
}
