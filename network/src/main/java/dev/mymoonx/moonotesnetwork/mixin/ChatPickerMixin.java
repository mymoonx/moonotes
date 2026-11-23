package dev.mymoonx.moonotesnetwork.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mymoonx.moonotesnetwork.ChatEmotePicker;
import dev.mymoonx.moonotesnetwork.MoonotesScreen;
import dev.mymoonx.moonotesnetwork.MoonotesEditBox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The picker owns its input while open; Enter in search can never reach chat submission. */
@Mixin(value=ChatScreen.class,remap=false)
public abstract class ChatPickerMixin extends Screen {
    @Shadow protected EditBox input;
    @Shadow private CommandSuggestions commandSuggestions;
    @Unique private final ChatEmotePicker moonotes$picker=new ChatEmotePicker();
    @Unique private EditBox moonotes$search,moonotes$groupName;
    @Unique private boolean moonotes$naming;
    @Unique private String moonotes$savedDraft;
    @Unique private int moonotes$savedCursor,moonotes$savedSelection;

    protected ChatPickerMixin(Component title){super(title);}

    @Inject(method="init",at=@At("HEAD"))
    private void moonotes$rememberDraft(CallbackInfo ci){
        if(input!=null){moonotes$savedDraft=input.getValue();moonotes$savedCursor=input.getCursorPosition();
            moonotes$savedSelection=((EditBoxSelectionAccessor)input).moonotes$highlightPosition();}
    }
    @Inject(method="init",at=@At("TAIL"))
    private void moonotes$init(CallbackInfo ci){
        moonotes$picker.resize(width,height);
        // Event child only: drawn once after the overlay in its own stratum.
        moonotes$search=addWidget(new MoonotesEditBox(font,moonotes$picker.searchX(),moonotes$picker.searchY(),
            moonotes$picker.searchWidth(),18,Component.literal("Поиск Moonotes")));
        moonotes$search.setMaxLength(80);
        moonotes$search.setResponder(moonotes$picker::searchChanged);
        moonotes$groupName=addWidget(new MoonotesEditBox(font,moonotes$picker.searchX(),moonotes$picker.searchY(),
            moonotes$picker.searchWidth(),18,Component.literal("Название группы Moonotes")));
        moonotes$groupName.setMaxLength(24);
        moonotes$groupName.setHint(Component.literal(moonotes$picker.groupNameHint()));
        moonotes$groupName.setResponder(moonotes$picker::groupNameChanged);
        if(moonotes$savedDraft!=null){input.setValue(moonotes$savedDraft);input.setCursorPosition(moonotes$savedCursor);input.setHighlightPos(moonotes$savedSelection);}
        moonotes$sync();
        if(moonotes$picker.isOpen()&&moonotes$activeField()!=null)setFocused(moonotes$activeField());
    }
    @Unique private EditBox moonotes$activeField(){return moonotes$picker.nameFieldVisible()?moonotes$groupName:moonotes$picker.searchVisible()?moonotes$search:null;}
    @Unique private void moonotes$sync(){
        if(moonotes$search==null||moonotes$groupName==null)return;
        for(EditBox field:new EditBox[]{moonotes$search,moonotes$groupName}){
            field.setX(moonotes$picker.searchX());field.setY(moonotes$picker.searchY());field.setWidth(moonotes$picker.searchWidth());
        }
        moonotes$search.setHint(Component.literal(moonotes$picker.inputHint()));
        if(!moonotes$search.getValue().equals(moonotes$picker.search()))moonotes$search.setValue(moonotes$picker.search());
        if(!moonotes$groupName.getValue().equals(moonotes$picker.groupName()))moonotes$groupName.setValue(moonotes$picker.groupName());
        moonotes$search.setVisible(moonotes$picker.searchVisible());moonotes$search.active=moonotes$picker.searchVisible();
        moonotes$groupName.setVisible(moonotes$picker.nameFieldVisible());moonotes$groupName.active=moonotes$picker.nameFieldVisible();
        input.setCanLoseFocus(true);
        EditBox activeField=moonotes$activeField();
        if(moonotes$picker.isOpen()){
            input.setFocused(false);
            if(activeField!=null&&getFocused()!=activeField)setFocused(activeField);
            else if(activeField==null){moonotes$search.setFocused(false);moonotes$groupName.setFocused(false);}
        }else{if(getFocused()!=input)setFocused(input);input.setCanLoseFocus(false);}
        if(moonotes$picker.nameFieldVisible()&&!moonotes$naming){moonotes$groupName.setCursorPosition(0);moonotes$groupName.setHighlightPos(moonotes$groupName.getValue().length());}
        moonotes$naming=moonotes$picker.nameFieldVisible();
    }
    @Unique private void moonotes$hideDuplicateSuggestions(){
        if(commandSuggestions!=null&&moonotes$picker.ownsSuggestions(input)){
            commandSuggestions.hide();input.setSuggestion(null);
        }
    }
    @Inject(method="extractRenderState",at=@At("HEAD"))
    private void moonotes$hideNativePopup(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta,CallbackInfo ci){
        moonotes$hideDuplicateSuggestions();
    }
    @Inject(method="extractRenderState",at=@At("TAIL"))
    private void moonotes$draw(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta,CallbackInfo ci){
        moonotes$sync();
        graphics.nextStratum();
        moonotes$picker.draw(graphics,font,mouseX,mouseY,input,moonotes$activeField()!=null&&moonotes$activeField().isFocused());
        if(moonotes$activeField()!=null){
            graphics.nextStratum();moonotes$activeField().extractRenderState(graphics,mouseX,mouseY,delta);
        }
    }
    @Inject(method="mouseClicked",at=@At("HEAD"),cancellable=true)
    private void moonotes$click(MouseButtonEvent event,boolean doubleClick,CallbackInfoReturnable<Boolean> cir){
        if(input==null)return;
        moonotes$hideDuplicateSuggestions();
        var action=moonotes$picker.click(event.x(),event.y(),event.button(),input,moonotes$activeField()!=null&&moonotes$activeField().isFocused());
        if(action==ChatEmotePicker.Click.NONE)return;
        moonotes$sync();
        if(action==ChatEmotePicker.Click.MANAGER){
            minecraft.gui.setScreen(new MoonotesScreen((Screen)(Object)this));
        }else if(action==ChatEmotePicker.Click.SEARCH&&moonotes$activeField()!=null){
            setFocused(moonotes$activeField());moonotes$activeField().mouseClicked(event,doubleClick);
            if(event.button()==InputConstants.MOUSE_BUTTON_LEFT)setDragging(true);
        }
        cir.setReturnValue(true);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){
        if(moonotes$picker.drag(event.x(),event.y()))return true;
        return super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event){
        if(moonotes$picker.releaseMouse())return true;
        return super.mouseReleased(event);
    }
    @Inject(method="mouseScrolled",at=@At("HEAD"),cancellable=true)
    private void moonotes$scroll(double x,double y,double horizontal,double vertical,CallbackInfoReturnable<Boolean> cir){
        moonotes$hideDuplicateSuggestions();
        if(moonotes$picker.scroll(x,y,vertical))cir.setReturnValue(true);
    }
    @Inject(method="keyPressed",at=@At("HEAD"),cancellable=true)
    private void moonotes$key(KeyEvent event,CallbackInfoReturnable<Boolean> cir){
        if(input==null)return;
        moonotes$hideDuplicateSuggestions();
        boolean wasOpen=moonotes$picker.isOpen();
        boolean handled=moonotes$picker.key(event,input,moonotes$activeField()!=null&&moonotes$activeField().isFocused());
        if(wasOpen){
            moonotes$sync();
            if(!handled&&moonotes$activeField()!=null)moonotes$activeField().keyPressed(event);
            cir.setReturnValue(true);
        }else if(handled){moonotes$sync();cir.setReturnValue(true);}
    }
    @Override public boolean keyReleased(KeyEvent event){
        moonotes$picker.keyReleased(event);
        return super.keyReleased(event);
    }
    @Override public boolean charTyped(CharacterEvent event){
        if(moonotes$picker.consumeCharacter(event.codepoint()))return true;
        if(moonotes$picker.isOpen()){
            if(moonotes$activeField()!=null)moonotes$activeField().charTyped(event);
            return true;
        }
        return super.charTyped(event);
    }
    @Inject(method="removed",at=@At("TAIL"))
    private void moonotes$removed(CallbackInfo ci){moonotes$picker.release();}
}
