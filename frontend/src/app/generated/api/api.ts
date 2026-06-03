export * from './authController.service';
import { AuthControllerService } from './authController.service';
export * from './ticketController.service';
import { TicketControllerService } from './ticketController.service';
export const APIS = [AuthControllerService, TicketControllerService];
