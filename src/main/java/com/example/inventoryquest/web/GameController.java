package com.example.inventoryquest.web;

import com.example.inventoryquest.combat.CombatException;
import com.example.inventoryquest.combat.VoteOption;
import com.example.inventoryquest.crafting.CraftingException;
import com.example.inventoryquest.game.GameException;
import com.example.inventoryquest.game.GameService;
import com.example.inventoryquest.inventory.InventoryException;
import com.example.inventoryquest.item.EquipSlot;
import com.example.inventoryquest.item.ItemType;
import com.example.inventoryquest.mountain.Direction;
import com.example.inventoryquest.player.Player;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The single-screen game UI. Every interaction is an htmx request that returns a re-rendered
 * server-side fragment; the browser holds no game state. Full navigations return the whole page;
 * htmx actions return the {@code screen} fragment (or a narrower one) to swap in place.
 */
@Controller
public class GameController {

    private final GameService game;

    public GameController(GameService game) {
        this.game = game;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/spawn")
    public String spawn(@RequestParam String name) {
        Player player = game.spawn(name.isBlank() ? "Climber" : name.trim());
        return "redirect:/game/" + player.getId();
    }

    @GetMapping("/game/{id}")
    public String screen(@PathVariable UUID id, Model model) {
        model.addAttribute("s", game.snapshot(id));
        return "game";
    }

    /** The context panel alone — what the WebSocket nudge re-fetches when the square changes. */
    @GetMapping("/game/{id}/panel")
    public String panel(@PathVariable UUID id, Model model) {
        model.addAttribute("s", game.snapshot(id));
        return "game :: contextPanel";
    }

    /** The live recipe filter: recipes containing every currently-selected ingredient. */
    @GetMapping("/game/{id}/recipes")
    public String recipes(@PathVariable UUID id,
                          @RequestParam(name = "sel", required = false) List<UUID> sel,
                          Model model) {
        Set<UUID> selected = sel == null ? Set.of() : new java.util.LinkedHashSet<>(sel);
        model.addAttribute("s", game.snapshot(id, selected, null));
        return "game :: recipePanel";
    }

    @PostMapping("/game/{id}/move")
    public String move(@PathVariable UUID id, @RequestParam Direction direction, Model model) {
        return act(id, model, () -> game.move(id, direction));
    }

    @PostMapping("/game/{id}/pickup/{itemId}")
    public String pickUp(@PathVariable UUID id, @PathVariable UUID itemId, Model model) {
        return act(id, model, () -> game.pickUp(id, itemId));
    }

    @PostMapping("/game/{id}/drop/{instanceId}")
    public String drop(@PathVariable UUID id, @PathVariable UUID instanceId, Model model) {
        return act(id, model, () -> game.drop(id, instanceId));
    }

    @PostMapping("/game/{id}/equip/{instanceId}")
    public String equip(@PathVariable UUID id, @PathVariable UUID instanceId, Model model) {
        return act(id, model, () -> game.equip(id, instanceId));
    }

    @PostMapping("/game/{id}/unequip/{slot}")
    public String unequip(@PathVariable UUID id, @PathVariable EquipSlot slot, Model model) {
        return act(id, model, () -> game.unequip(id, slot));
    }

    @PostMapping("/game/{id}/eat/{instanceId}")
    public String eat(@PathVariable UUID id, @PathVariable UUID instanceId, Model model) {
        return act(id, model, () -> game.eat(id, instanceId));
    }

    @PostMapping("/game/{id}/craft/{result}")
    public String craft(@PathVariable UUID id, @PathVariable ItemType result, Model model) {
        return act(id, model, () -> game.craft(id, result));
    }

    @PostMapping("/game/{id}/vote")
    public String vote(@PathVariable UUID id, @RequestParam VoteOption option, Model model) {
        return act(id, model, () -> game.castVote(id, option));
    }

    @PostMapping("/game/{id}/fight/attack/{targetId}")
    public String attack(@PathVariable UUID id, @PathVariable UUID targetId, Model model) {
        return act(id, model, () -> game.attack(id, targetId));
    }

    @PostMapping("/game/{id}/fight/parley")
    public String parley(@PathVariable UUID id, Model model) {
        return act(id, model, () -> game.parley(id));
    }

    @PostMapping("/game/{id}/fight/parley/accept")
    public String acceptParley(@PathVariable UUID id, Model model) {
        return act(id, model, () -> game.answerParley(id, true));
    }

    @PostMapping("/game/{id}/fight/parley/reject")
    public String rejectParley(@PathVariable UUID id, Model model) {
        return act(id, model, () -> game.answerParley(id, false));
    }

    @PostMapping("/game/{id}/trade/{tableId}/offer/{instanceId}")
    public String offer(@PathVariable UUID id, @PathVariable String tableId,
                        @PathVariable UUID instanceId, Model model) {
        return act(id, model, () -> game.offer(id, tableId, instanceId));
    }

    @PostMapping("/game/{id}/trade/{tableId}/withdraw/{instanceId}")
    public String withdraw(@PathVariable UUID id, @PathVariable String tableId,
                           @PathVariable UUID instanceId, Model model) {
        return act(id, model, () -> game.withdraw(id, tableId, instanceId));
    }

    @PostMapping("/game/{id}/trade/{tableId}/propose")
    public String propose(@PathVariable UUID id, @PathVariable String tableId, Model model) {
        return act(id, model, () -> game.propose(id, tableId));
    }

    @PostMapping("/game/{id}/trade/{tableId}/accept")
    public String accept(@PathVariable UUID id, @PathVariable String tableId, Model model) {
        return act(id, model, () -> game.acceptTrade(id, tableId));
    }

    @PostMapping("/game/{id}/trade/{tableId}/reject")
    public String reject(@PathVariable UUID id, @PathVariable String tableId, Model model) {
        return act(id, model, () -> game.rejectTrade(id, tableId));
    }

    /** Run a mutation, then re-render the whole screen — with the error surfaced if it was illegal. */
    private String act(UUID id, Model model, Runnable action) {
        String message = null;
        try {
            action.run();
        } catch (InventoryException | CraftingException | GameException | CombatException e) {
            message = e.getMessage();
        }
        model.addAttribute("s", game.snapshot(id, Set.of(), message));
        return "game :: screen";
    }
}
